from math import cos, radians, sin

FACE_KEYS = ('north', 'east', 'south', 'west', 'up', 'down')
TEXTURE_SIZE = 512
DEFAULT_SCALE = 2.0
HERO_SCALE = 2.5
EDGE_SCALE = 1.5
ATLAS_GAP = 2

CUBE_SPECS = []


def add(name, group, frm, to, base, overrides=None, rotation=(0, 0, 0), pivot=None):
    CUBE_SPECS.append({
        'name': name,
        'group': group,
        'from': [round(float(v), 3) for v in frm],
        'to': [round(float(v), 3) for v in to],
        'base': base,
        'overrides': overrides or {},
        'rotation': [float(v) for v in rotation],
        'pivot': list(pivot) if pivot is not None else None,
    })


# Main housing: 44.8 BB wide, shifted right to make room for the 19.2 BB black duct.
add('main_shell', 'housing', [-12.8, 4, -18], [32, 28, 18], 'shell')
add('upper_roof', 'housing', [-11.8, 28, -16], [31, 29.5, 18], 'shell', {'south': 'rail'})
add('front_bumper', 'housing', [-13.5, 4.5, 17], [32, 8.5, 24], 'steel', {'south': 'bumper'})
add('rear_backplate', 'housing', [-11.8, 4, -20], [31, 28, -18], 'shell_dark', {'south': 'dark_metal'})

# Three louver banks, shared bezel bars, dividers and sill.
add('vent_bay_left', 'front_vents', [-7.3, 11.5, 18], [0.7, 25.5, 20.2], 'vent_edge', {'south': 'vent'})
add('vent_bay_center', 'front_vents', [2.2, 11.5, 18], [10.2, 25.5, 20.2], 'vent_edge', {'south': 'vent'})
add('vent_bay_right', 'front_vents', [11.7, 11.5, 18], [19.7, 25.5, 20.2], 'vent_edge', {'south': 'vent'})
add('vent_divider_left', 'front_vents', [1.2, 10.5, 19.0], [2.2, 26.0, 20.4], 'steel', {'south': 'dark_metal', 'north': 'dark_metal'})
add('vent_divider_right', 'front_vents', [10.2, 10.5, 19.0], [11.2, 26.0, 20.4], 'steel', {'south': 'dark_metal', 'north': 'dark_metal'})
add('vent_sill', 'front_vents', [-7.3, 10.0, 19.0], [20.2, 11.5, 20.6], 'trim', {'north': 'panel_edge', 'down': 'dark_metal'})
add('vent_top_bezel', 'front_vents', [-7.8, 25.5, 18.0], [20.2, 26.7, 21.0], 'steel', {'south': 'trim'})
add('vent_left_bezel', 'front_vents', [-8.8, 10.0, 18.0], [-7.8, 26.7, 21.0], 'steel', {'west': 'trim'})
add('vent_right_bezel', 'front_vents', [20.2, 10.0, 18.0], [21.2, 26.7, 21.0], 'steel', {'east': 'trim'})

# Right control console with a real raised four-piece bezel.
add('control_backplate', 'control_panel', [20.2, 7.5, 18.0], [30.7, 26.5, 20.5], 'panel_edge', {'up': 'steel', 'down': 'dark_metal'})
add('control_face', 'control_panel', [21.0, 8.3, 20.5], [29.9, 25.7, 20.9], 'panel_edge', {'south': 'control'})
add('outlet_left', 'control_panel', [21.6, 4.8, 18.5], [24.7, 7.3, 20.7], 'rubber', {'south': 'outlet', 'east': 'steel', 'west': 'steel'})
add('outlet_right', 'control_panel', [26.2, 4.8, 18.5], [29.3, 7.3, 20.7], 'rubber', {'south': 'outlet', 'east': 'steel', 'west': 'steel'})
add('control_rim_top', 'control_panel', [19.9, 26.0, 20.2], [31.0, 27.0, 21.3], 'steel', {'south': 'trim'})
add('control_rim_bottom', 'control_panel', [19.9, 6.9, 20.2], [31.0, 8.0, 21.3], 'steel', {'south': 'trim'})
add('control_rim_left', 'control_panel', [19.9, 8.0, 20.2], [20.8, 26.0, 21.3], 'steel', {'west': 'trim'})
add('control_rim_right', 'control_panel', [30.1, 8.0, 20.2], [31.0, 26.0, 21.3], 'steel', {'east': 'trim'})

# Large black external duct: about 19.2 BB wide and 22 BB high.
add('black_duct_shell', 'black_duct', [-32, 5, -18], [-12.8, 27, 16], 'black_metal', {'south': 'black_metal'})
add('black_duct_top_frame', 'black_duct', [-31.5, 22, -17], [-13.2, 27, 19], 'black_frame')
add('black_duct_mid_frame', 'black_duct', [-31.5, 16.5, -17], [-13.2, 18, 18.5], 'black_frame')
add('black_duct_bottom_frame', 'black_duct', [-31.5, 5, -17], [-13.2, 7, 19], 'black_frame')
add('black_duct_left_frame', 'black_duct', [-32, 7, -17], [-30.5, 22, 19], 'black_frame')
add('black_duct_right_frame', 'black_duct', [-14.3, 7, -17], [-12.8, 22, 19], 'black_frame')
add('black_duct_upper_grille', 'black_duct', [-30.3, 18, 16.2], [-14.5, 22, 17.2], 'black_grille')
add('black_grille_bar_upper', 'black_duct', [-29.5, 18.7, 17.2], [-15.5, 19.3, 18.0], 'black_frame')
add('black_grille_bar_lower', 'black_duct', [-29.5, 20.3, 17.2], [-15.5, 20.9, 18.0], 'black_frame')
add('black_duct_service_door', 'black_duct', [-30.0, 7.5, 16.2], [-14.8, 16.0, 17.4], 'black_door')
add('black_duct_door_inset', 'black_duct', [-29.0, 8.5, 17.4], [-15.8, 15.0, 17.9], 'black_metal')
add('black_duct_handle', 'black_duct', [-17.3, 11, 17.9], [-15.5, 13, 18.6], 'trim')
add('black_duct_bracket', 'black_duct', [-13.2, 8, -12], [-10.5, 24, 10], 'steel', {'south': 'dark_metal'})

# Top rigging, roof hatch, and two end caps that tie into the widened body.
add('top_rail_left', 'top_rigging', [-11.5, 29.5, -6.0], [-8.0, 31.5, 16.0], 'rail', {'south': 'steel', 'north': 'steel', 'east': 'steel', 'west': 'steel'})
add('top_rail_right', 'top_rigging', [26.5, 29.5, -6.0], [30.0, 31.5, 16.0], 'rail', {'south': 'steel', 'north': 'steel', 'east': 'steel', 'west': 'steel'})
add('top_crossbar_front', 'top_rigging', [-13.5, 30.5, 14.0], [32.0, 32.0, 16.5], 'rail', {'south': 'steel', 'north': 'steel', 'east': 'steel', 'west': 'steel'})
add('top_crossbar_rear', 'top_rigging', [-13.5, 30.2, -7.0], [32.0, 31.7, -5.0], 'rail', {'south': 'steel', 'north': 'steel', 'east': 'steel', 'west': 'steel'})
add('top_support_left', 'top_rigging', [-11.0, 28.0, 12.0], [-9.0, 30.0, 14.0], 'steel')
add('top_support_right', 'top_rigging', [28.0, 28.0, 12.0], [30.0, 30.0, 14.0], 'steel')
add('roof_hatch', 'top_rigging', [1.6, 29.5, -12.0], [17.6, 31.0, 1.0], 'hatch')
add('top_end_cap_left', 'top_rigging', [-14.3, 29.5, 12.0], [-13.5, 32.0, 17.0], 'black_frame')
add('top_end_cap_right', 'top_rigging', [31.2, 29.5, 12.0], [32.0, 32.0, 17.0], 'steel')

# Red base skirt, feet, and two front corner guards.
add('front_skirt', 'base_skirt', [-14.0, 0.8, 18.0], [32.0, 5.0, 22.0], 'red', {'down': 'rubber'})
add('rear_skirt', 'base_skirt', [-14.0, 0.8, -22.0], [32.0, 5.0, -18.0], 'red', {'down': 'rubber'})
add('left_skirt', 'base_skirt', [-14.0, 0.8, -18.0], [-12.8, 5.0, 18.0], 'red', {'down': 'rubber'})
add('right_skirt', 'base_skirt', [30.8, 0.8, -18.0], [32.0, 5.0, 18.0], 'red', {'down': 'rubber'})
for name, x1, x2, z1, z2 in (
    ('foot_front_left', -11, -6, 16, 22),
    ('foot_front_right', 26, 31, 16, 22),
    ('foot_rear_left', -11, -6, -22, -16),
    ('foot_rear_right', 26, 31, -22, -16),
):
    add(name, 'base_skirt', [x1, 0, z1], [x2, 2.2, z2], 'rubber')
add('corner_guard_left', 'base_skirt', [-14.2, 0.8, 20.0], [-12.8, 6.5, 23.0], 'steel')
add('corner_guard_right', 'base_skirt', [30.8, 0.8, 20.0], [32.0, 6.5, 23.0], 'steel')

# Rear octagonal static fan housing, centered on the widened main body.
center_x, center_y, center_z = 9.6, 16.0, -22.6
radius = 9.2
ring_len = 7.2
ring_thick = 2.0
ring_depth = 2.2
for i in range(8):
    phi = radians(90 + i * 45)
    cx = center_x + radius * cos(phi)
    cy = center_y + radius * sin(phi)
    angle = i * 45
    add(
        f'fan_ring_{i}', 'rear_rotor',
        [cx - ring_len / 2, cy - ring_thick / 2, center_z - ring_depth / 2],
        [cx + ring_len / 2, cy + ring_thick / 2, center_z + ring_depth / 2],
        'fan_metal', {'up': 'steel'}, rotation=(0, 0, angle), pivot=(cx, cy, center_z),
    )

for i in range(4):
    angle = i * 90
    a = radians(angle)
    cx = center_x + 6.9 * cos(a)
    cy = center_y + 6.9 * sin(a)
    add(
        f'fan_support_{i}', 'rear_rotor',
        [cx - 6.8, cy - 0.55, -23.15],
        [cx + 6.8, cy + 0.55, -22.35],
        'fan_metal', {'up': 'steel'}, rotation=(0, 0, angle), pivot=(cx, cy, -22.75),
    )

for i in range(4):
    angle = 45 + i * 90
    a = radians(angle)
    cx = center_x + 3.7 * cos(a)
    cy = center_y + 3.7 * sin(a)
    add(
        f'fan_blade_{i}', 'rear_rotor',
        [cx - 4.4, cy - 1.05, -23.8],
        [cx + 4.4, cy + 1.05, -22.9],
        'fan_blade', {'up': 'steel', 'down': 'dark_metal'},
        rotation=(0, 0, angle), pivot=(cx, cy, -23.35),
    )

add('fan_hub', 'rear_rotor', [7.8, 14.2, -24.0], [11.4, 17.8, -22.8], 'hub', {'north': 'hub', 'south': 'hub'})
add('fan_mount_left', 'rear_rotor', [-0.7, 14.5, -22.2], [1.2, 17.5, -17.6], 'steel')
add('fan_mount_right', 'rear_rotor', [18.0, 14.5, -22.2], [19.9, 17.5, -17.6], 'steel')
add('rear_panel_left', 'rear_rotor', [-9.9, 5.0, -20.2], [-3.4, 11.0, -18.5], 'hatch')
add('rear_panel_right', 'rear_rotor', [22.6, 5.0, -20.2], [29.1, 11.0, -18.5], 'hatch')
add('rear_control_box', 'rear_rotor', [-9.9, 18.0, -20.5], [-4.4, 26.0, -18.5], 'panel_edge', {'north': 'control'})

GROUP_ORDER = ['engine', 'housing', 'front_vents', 'control_panel', 'black_duct', 'base_skirt', 'top_rigging', 'rear_rotor']

HERO_FACES = {
    ('main_shell', 'south'), ('main_shell', 'north'), ('main_shell', 'east'),
    ('main_shell', 'west'), ('main_shell', 'up'),
    ('vent_bay_left', 'south'), ('vent_bay_center', 'south'), ('vent_bay_right', 'south'),
    ('control_face', 'south'), ('front_bumper', 'south'),
    ('black_duct_shell', 'south'), ('black_duct_shell', 'west'),
    ('black_duct_upper_grille', 'south'), ('black_duct_service_door', 'south'),
    ('black_duct_door_inset', 'south'), ('black_duct_left_frame', 'west'),
    ('black_duct_right_frame', 'west'), ('black_duct_top_frame', 'south'),
    ('black_duct_mid_frame', 'south'), ('black_duct_bottom_frame', 'south'),
    ('roof_hatch', 'up'), ('rear_panel_left', 'north'), ('rear_panel_right', 'north'),
    ('fan_blade_0', 'north'), ('fan_blade_1', 'north'), ('fan_blade_2', 'north'), ('fan_blade_3', 'north'),
    ('fan_blade_0', 'south'), ('fan_blade_1', 'south'), ('fan_blade_2', 'south'), ('fan_blade_3', 'south'),
}


def face_dimensions(spec, face):
    frm = spec['from']
    to = spec['to']
    dim = [abs(to[i] - frm[i]) for i in range(3)]
    return {
        'north': (dim[0], dim[1]),
        'south': (dim[0], dim[1]),
        'east': (dim[2], dim[1]),
        'west': (dim[2], dim[1]),
        'up': (dim[0], dim[2]),
        'down': (dim[0], dim[2]),
    }[face]


def face_style(spec, face):
    return spec['overrides'].get(face, spec['base'])


def face_scale(spec, face, width_bb, height_bb):
    if (spec['name'], face) in HERO_FACES:
        return HERO_SCALE
    if min(width_bb, height_bb) < 0.75:
        return EDGE_SCALE
    style = face_style(spec, face)
    if style in ('rubber', 'dark_metal', 'panel_edge', 'black_frame', 'vent_edge'):
        return EDGE_SCALE
    return DEFAULT_SCALE


def build_face_layout(texture_size=TEXTURE_SIZE, gap=ATLAS_GAP):
    items = []
    for spec in CUBE_SPECS:
        for face in FACE_KEYS:
            width_bb, height_bb = face_dimensions(spec, face)
            scale = face_scale(spec, face, width_bb, height_bb)
            width_px = max(1, round(width_bb * scale))
            height_px = max(1, round(height_bb * scale))
            items.append((spec['name'], face, width_px, height_px, width_bb, height_bb, scale, spec))
    items.sort(key=lambda item: (-item[3], -item[2], item[0], item[1]))
    x = 0
    y = 0
    row_height = 0
    regions = {}
    for name, face, w, h, width_bb, height_bb, scale, spec in items:
        if x + w > texture_size:
            x = 0
            y += row_height + gap
            row_height = 0
        if y + h > texture_size:
            raise RuntimeError(f'UV atlas overflow at {name}/{face}: y={y}, h={h}')
        regions[f'{name}|{face}'] = {
            'rect': [x, y, x + w, y + h],
            'width_bb': round(width_bb, 3),
            'height_bb': round(height_bb, 3),
            'width_px': w,
            'height_px': h,
            'px_per_bb': scale,
            'style': face_style(spec, face),
        }
        x += w + gap
        row_height = max(row_height, h)
    return {
        'texture_size': texture_size,
        'default_px_per_bb': DEFAULT_SCALE,
        'hero_px_per_bb': HERO_SCALE,
        'edge_px_per_bb': EDGE_SCALE,
        'gap_px': gap,
        'regions': regions,
    }
