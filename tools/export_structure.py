"""从 Minecraft 存档 region 读取方块、导出结构模板（纯标准库实现）。

结构 NBT 按 1.20.1 StructureTemplate 格式：
  {DataVersion:int, size:[x,y,z], palette:[{Name,Properties?}], blocks:[{pos,state,nbt?}], entities:[]}

方块实体 NBT 原样搬运（保留 byte/short/int/long 等原始类型，柜子/机器内容不丢），
和现有 starter_sub.nbt 一致（里面本来就带 x/y/z/id，放置时无害）。

用法：
  python tools/export_structure.py inspect --save "<saves/世界>" [--dim dimensions/barotrauma/the_deep] \
      --min x y z --max x y z
  python tools/export_structure.py export --save "<saves/世界>" --out xxx.nbt \
      [--dim ...] [--min ...] [--max ...] [--exclude "minecraft:smooth_stone,..."]
"""
import argparse
import gzip
import os
import struct
import sys
import zlib

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

PLATFORM_BLOCKS = {"minecraft:smooth_stone", "minecraft:polished_andesite"}


# ------------------------------------------------------------------ 读取（带类型）

class Reader:
    def __init__(self, data, pos=0):
        self.d = data
        self.p = pos

    def take(self, n):
        v = self.d[self.p:self.p + n]
        self.p += n
        return v

    def u1(self):
        return self.take(1)[0]

    def unpack(self, fmt, size):
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        n = self.unpack(">H", 2)
        return self.take(n).decode("utf-8", "replace")


def read_payload(r, tag):
    """返回 (type, value)。compound: {name: (type, value)}；list: (elem_type, [(type,value)...])。"""
    if tag == TAG_BYTE:
        return tag, r.unpack(">b", 1)
    if tag == TAG_SHORT:
        return tag, r.unpack(">h", 2)
    if tag == TAG_INT:
        return tag, r.unpack(">i", 4)
    if tag == TAG_LONG:
        return tag, r.unpack(">q", 8)
    if tag == TAG_FLOAT:
        return tag, r.unpack(">f", 4)
    if tag == TAG_DOUBLE:
        return tag, r.unpack(">d", 8)
    if tag == TAG_BYTE_ARRAY:
        n = r.unpack(">i", 4)
        return tag, list(r.take(n))
    if tag == TAG_STRING:
        return tag, r.string()
    if tag == TAG_LIST:
        elem_type = r.u1()
        n = r.unpack(">i", 4)
        items = [read_payload(r, elem_type) for _ in range(n)]
        return tag, (elem_type, items)
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            t = r.u1()
            if t == TAG_END:
                return tag, out
            name = r.string()
            out[name] = read_payload(r, t)
    if tag == TAG_INT_ARRAY:
        n = r.unpack(">i", 4)
        return tag, [r.unpack(">i", 4) for _ in range(n)]
    if tag == TAG_LONG_ARRAY:
        n = r.unpack(">i", 4)
        return tag, [r.unpack(">q", 8) for _ in range(n)]
    raise ValueError("未知 NBT tag " + str(tag))


def parse_nbt_bytes(raw):
    r = Reader(raw)
    tag = r.u1()
    r.string()  # 根名字
    return read_payload(r, tag)


def read_nbt_file(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return parse_nbt_bytes(raw)


def plain(node):
    """带类型的节点 → 普通 python 值。"""
    tag, value = node
    if tag == TAG_COMPOUND:
        return {k: plain(v) for k, v in value.items()}
    if tag == TAG_LIST:
        return [plain(v) for v in value[1]]
    return value


def read_chunk(region_path, cx, cz):
    with open(region_path, "rb") as f:
        header = f.read(4096)
        if len(header) < 4096:
            return None
        entry = struct.unpack_from(">I", header, ((cx & 31) + (cz & 31) * 32) * 4)[0]
        if entry == 0:
            return None
        f.seek((entry >> 8) * 4096)
        length = struct.unpack(">I", f.read(4))[0]
        comp = f.read(1)[0]
        payload = f.read(length - 1)
    if comp == 1:
        payload = gzip.decompress(payload)
    elif comp == 2:
        payload = zlib.decompress(payload)
    elif comp != 3:
        raise ValueError("未知区块压缩 " + str(comp))
    return parse_nbt_bytes(payload)


def decode_section(section):
    """section(普通值) → (sectionY, {(lx,ly,lz): state_string})。"""
    y = section.get("Y", 0)
    bs = section.get("block_states")
    if not bs:
        return y, {}
    palette = bs.get("palette", [])
    data = bs.get("data")
    states = []
    for entry in palette:
        name = entry.get("Name", "minecraft:air")
        props = entry.get("Properties") or {}
        if props:
            name += "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"
        states.append(name)
    if not data:
        fill = states[0] if states else "minecraft:air"
        return y, {(x, yy, z): fill for x in range(16) for yy in range(16) for z in range(16)}
    bits = max(4, (len(states) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    out = {}
    i = 0
    for yy in range(16):
        for z in range(16):
            for x in range(16):
                idx = (data[i // per_long] >> ((i % per_long) * bits)) & mask
                out[(x, yy, z)] = states[idx] if idx < len(states) else "minecraft:air"
                i += 1
    return y, out


def iter_region_blocks(save_dir, dim, minp, maxp):
    """产出 ((wx,wy,wz), state_string, be_typed_node|None)。"""
    region_dir = os.path.join(save_dir, dim, "region") if dim else os.path.join(save_dir, "region")
    be_map = {}
    blocks = {}
    for cx in range(minp[0] >> 4, (maxp[0] >> 4) + 1):
        for cz in range(minp[2] >> 4, (maxp[2] >> 4) + 1):
            path = os.path.join(region_dir, f"r.{cx >> 5}.{cz >> 5}.mca")
            if not os.path.exists(path):
                continue
            data = read_chunk(path, cx, cz)
            if data is None:
                continue
            root = plain(data)
            for be in root.get("block_entities") or []:
                be_map[(be.get("x"), be.get("y"), be.get("z"))] = be
            for section in root.get("sections") or []:
                sy, sec_blocks = decode_section(section)
                base_y = sy * 16
                for (lx, ly, lz), state in sec_blocks.items():
                    wx = (cx << 4) + lx
                    wy = base_y + ly
                    wz = (cz << 4) + lz
                    if minp[0] <= wx <= maxp[0] and minp[1] <= wy <= maxp[1] and minp[2] <= wz <= maxp[2]:
                        blocks[(wx, wy, wz)] = state
    # 方块实体按 typed 再取一遍，保留原始类型（上面 plain 只用来拿坐标）
    typed_be = {}
    for cx in range(minp[0] >> 4, (maxp[0] >> 4) + 1):
        for cz in range(minp[2] >> 4, (maxp[2] >> 4) + 1):
            path = os.path.join(region_dir, f"r.{cx >> 5}.{cz >> 5}.mca")
            if not os.path.exists(path):
                continue
            data = read_chunk(path, cx, cz)
            if data is None:
                continue
            _, root_typed = data
            for be_node in (root_typed.get("block_entities") or (TAG_LIST, (TAG_COMPOUND, [])))[1][1]:
                tag, be = be_node
                pos = (plain(be["x"]), plain(be["y"]), plain(be["z"]))
                typed_be[pos] = be_node
    for pos, state in blocks.items():
        yield pos, state, typed_be.get(pos)


# ------------------------------------------------------------------ 写入

def w_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def w_tag(tag, name, payload):
    return bytes([tag]) + w_string(name) + payload


def w_i32(v):
    return struct.pack(">i", v)


def w_compound(payload):
    return payload + b"\x00"


def w_list_int(values):
    return bytes([TAG_INT]) + w_i32(len(values)) + b"".join(w_i32(v) for v in values)


def w_state(name_with_props):
    """`barotrauma:vent[facing=north]` → palette 条目 compound。"""
    if "[" in name_with_props:
        name, rest = name_with_props.split("[", 1)
        props = rest.rstrip("]").split(",")
        body = w_tag(TAG_STRING, "Name", w_string(name))
        entries = b""
        for pair in props:
            if not pair:
                continue
            k, v = pair.split("=", 1)
            entries += w_tag(TAG_STRING, k, w_string(v))
        body += w_tag(TAG_COMPOUND, "Properties", w_compound(entries))
        return w_compound(body)
    return w_compound(w_tag(TAG_STRING, "Name", w_string(name_with_props)))


def write_typed(buf, node):
    """把读出来的带类型节点原样写回（类型与内容都不变）。"""
    tag, value = node
    if tag == TAG_BYTE:
        buf += struct.pack(">b", value)
    elif tag == TAG_SHORT:
        buf += struct.pack(">h", value)
    elif tag == TAG_INT:
        buf += struct.pack(">i", value)
    elif tag == TAG_LONG:
        buf += struct.pack(">q", value)
    elif tag == TAG_FLOAT:
        buf += struct.pack(">f", value)
    elif tag == TAG_DOUBLE:
        buf += struct.pack(">d", value)
    elif tag == TAG_BYTE_ARRAY:
        buf += w_i32(len(value)) + bytes(value)
    elif tag == TAG_STRING:
        buf += w_string(value)
    elif tag == TAG_LIST:
        elem_type, items = value
        buf += bytes([elem_type]) + w_i32(len(items))
        for item in items:
            write_typed(buf, item)
    elif tag == TAG_COMPOUND:
        for name, child in value.items():
            buf += bytes([child[0]]) + w_string(name)
            write_typed(buf, child)
        buf += b"\x00"
    elif tag == TAG_INT_ARRAY:
        buf += w_i32(len(value)) + b"".join(w_i32(v) for v in value)
    elif tag == TAG_LONG_ARRAY:
        buf += w_i32(len(value)) + b"".join(struct.pack(">q", v) for v in value)
    else:
        raise ValueError("不能写 tag " + str(tag))


def write_structure(path, data_version, size, blocks, entity_blocks):
    palette = []
    index = {}
    body = b""
    for pos, state, be in blocks:
        if state not in index:
            index[state] = len(palette)
            palette.append(state)
        entry = w_tag(TAG_LIST, "pos", w_list_int(list(pos)))
        entry += w_tag(TAG_INT, "state", w_i32(index[state]))
        if be is not None:
            buf = bytearray()
            write_typed(buf, be)
            entry += w_tag(TAG_COMPOUND, "nbt", bytes(buf))
        body += w_compound(entry)
    root = w_tag(TAG_INT, "DataVersion", w_i32(data_version))
    root += w_tag(TAG_LIST, "size", w_list_int(size))
    root += w_tag(TAG_LIST, "palette",
                  bytes([TAG_COMPOUND]) + w_i32(len(palette)) + b"".join(w_state(s) for s in palette))
    root += w_tag(TAG_LIST, "blocks",
                  bytes([TAG_COMPOUND]) + w_i32(len(blocks)) + body)
    root += w_tag(TAG_LIST, "entities", bytes([TAG_END]) + w_i32(0))
    out = bytes([TAG_COMPOUND]) + w_string("") + w_compound(root)
    with open(path, "wb") as f:
        f.write(gzip.compress(out))


# ------------------------------------------------------------------ 命令

def collect(save, dim, minp, maxp, exclude):
    picked = []
    for pos, state, be in iter_region_blocks(save, dim, minp, maxp):
        base = state.split("[")[0]
        if base in exclude:
            continue
        picked.append((pos, state, be))
    return picked


def cmd_inspect(args):
    exclude = {"minecraft:air"} | PLATFORM_BLOCKS
    picked = collect(args.save, args.dim, tuple(args.min), tuple(args.max), exclude)
    counts = {}
    for pos, state, be in picked:
        base = state.split("[")[0]
        counts[base] = counts.get(base, 0) + 1
    xs = [p[0][0] for p in picked]
    ys = [p[0][1] for p in picked]
    zs = [p[0][2] for p in picked]
    print("== 方块统计 ==")
    for name, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"{n:6d}  {name}")
    print("\n非空气 + 排除平台后的包围盒")
    print("min", (min(xs), min(ys), min(zs)), "max", (max(xs), max(ys), max(zs)))
    print("size", (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1))
    print("block entities", sum(1 for p in picked if p[2] is not None))
    return 0


def as_blockpos_long(x, y, z):
    """BlockPos.asLong 的 Python 版本（x<<38 | z<<12 | y，各段按位截断）。"""
    u = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return u - (1 << 64) if u >= (1 << 63) else u


def from_blockpos_long(v):
    u = v & ((1 << 64) - 1)
    x = u >> 38
    y = u & 0xFFF
    z = (u >> 12) & 0x3FFFFFF
    if x >= 1 << 25:
        x -= 1 << 26
    if y >= 1 << 11:
        y -= 1 << 12
    if z >= 1 << 25:
        z -= 1 << 26
    return x, y, z


def prepare_for_blueprint(blocks, origin, size=None):
    """蓝图化：绑定坐标转相对、清损伤、耐久归一。blocks 里的 be 是带类型节点。"""
    rebased = 0

    def inside(p):
        return (size is None
                or (0 <= p[0] < size[0] and 0 <= p[1] < size[1] and 0 <= p[2] < size[2]))

    for _, _, be in blocks:
        if be is None:
            continue
        fields = be[1]
        for key in ("PowerBox", "StorageBox"):
            node = fields.get(key)
            if node is None or node[0] != TAG_LONG:
                continue
            wx, wy, wz = from_blockpos_long(node[1])
            rel = (wx - origin[0], wy - origin[1], wz - origin[2])
            # 绑定必须落在模板包围盒里。世界坐标明显不在船上时，说明这个值
            # 本来就是相对坐标（例如上一版蓝图生成后没重定基的电池），别再减原点。
            if not inside(rel):
                if inside((wx, wy, wz)):
                    rel = (wx, wy, wz)
                else:
                    # 指向模板外的绑定没有意义，直接解绑（回默认总线）。
                    print("  ! 丢弃越界绑定 %s=%s" % (key, (wx, wy, wz)))
                    del fields[key]
                    continue
            fields[key] = (TAG_LONG, as_blockpos_long(*rel))
            rebased += 1
        if "BarotraumaHullDamage" in fields:
            fields["BarotraumaHullDamage"] = (TAG_LIST, (TAG_END, []))
        if "BarotraumaKnownInterior" in fields:
            fields["BarotraumaKnownInterior"] = (TAG_LONG_ARRAY, [])
        if "Condition" in fields:
            fields["Condition"] = (TAG_DOUBLE, 100.0)
        if "ConditionGrace" in fields:
            fields["ConditionGrace"] = (TAG_LONG, 0)
    return rebased


def cmd_export(args):
    if args.min is None or args.max is None:
        print("导出需要 --min/--max 扫描窗口，例如 --min -60 266 -30 --max 48 320 30",
              file=sys.stderr)
        return 1
    exclude = {"minecraft:air"} | PLATFORM_BLOCKS
    picked = collect(args.save, args.dim, tuple(args.min), tuple(args.max), exclude)
    if not picked:
        print("范围内没有方块", file=sys.stderr)
        return 1
    # 包围盒按"非空气、非平台"的方块自动收紧
    minp = tuple(min(p[0][i] for p in picked) for i in range(3))
    maxp = tuple(max(p[0][i] for p in picked) for i in range(3))
    size = tuple(maxp[i] - minp[i] + 1 for i in range(3))
    rel = [((pos[0] - minp[0], pos[1] - minp[1], pos[2] - minp[2]), state, be)
           for pos, state, be in picked]
    rebased = prepare_for_blueprint(rel, minp, size)
    version_node = read_nbt_file(os.path.join(args.save, "level.dat"))
    data_version = plain(version_node)["Data"]["DataVersion"]
    write_structure(args.out, data_version, size, rel, None)
    print("导出完成:", args.out)
    print("  size", size, "blocks", len(rel), "DataVersion", data_version)
    print("  原点(世界坐标)", minp, " 包围盒 max", maxp)
    print("  蓝图化：", rebased, "个绑定改成相对坐标；损伤/已知舱内格清零；耐久归一 100")
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name, fn in (("inspect", cmd_inspect), ("export", cmd_export)):
        p = sub.add_parser(name)
        p.add_argument("--save", required=True)
        p.add_argument("--dim", default="")
        p.add_argument("--min", nargs=3, type=int)
        p.add_argument("--max", nargs=3, type=int)
        p.add_argument("--out", default="")
        p.set_defaults(func=fn)
    args = ap.parse_args()
    sys.exit(args.func(args))
