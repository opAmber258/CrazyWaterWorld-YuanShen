package com.opamb.barotrauma.data;

import com.opamb.barotrauma.campaign.CampaignState;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 旧版按玩家 credits → 战役共享钱包的一次性迁移。 */
class MissionSavedDataWalletTest {

    @Test
    void 旧版按玩家余额一次性迁入共享钱包() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        CompoundTag creditsTag = new CompoundTag();
        creditsTag.putInt(a.toString(), 80);
        creditsTag.putInt(b.toString(), 120);
        tag.put("credits", creditsTag);

        MissionSavedData data = MissionSavedData.load(tag);
        CampaignState wallet = new CampaignState();
        int before = wallet.credits();

        assertEquals(200, data.migrateLegacyCreditsTo(wallet));
        assertEquals(before + 200, wallet.credits());
        // 第二次不能再加一遍
        assertEquals(0, data.migrateLegacyCreditsTo(wallet));
        assertEquals(before + 200, wallet.credits());
    }

    @Test
    void 负数余额不会倒扣共享钱包() {
        UUID a = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        CompoundTag creditsTag = new CompoundTag();
        creditsTag.putInt(a.toString(), -50);
        tag.put("credits", creditsTag);

        MissionSavedData data = MissionSavedData.load(tag);
        CampaignState wallet = new CampaignState();
        int before = wallet.credits();

        assertEquals(0, data.migrateLegacyCreditsTo(wallet));
        assertEquals(before, wallet.credits());
    }
}
