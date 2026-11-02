package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Filter or Priority upgrade. Unlike Speed and Capacity, which belong to a whole pipe, these sit
 * in one pipe at one face. Used on a pipe connector it goes into that pipe at that face, if the
 * pipe can take it and has none there yet; the pipe screen can also put it in and take it out.
 *
 * <p>This is not an {@link UpgradeItem} on purpose: machines and the pipe's own upgrade slots
 * accept any {@code UpgradeItem}, and neither has a use for these.
 */
public class EndpointUpgradeItem extends Item {
    private final EndpointSettings.Upgrade upgrade;

    public EndpointUpgradeItem(EndpointSettings.Upgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    /** Which upgrade this item installs. */
    public EndpointSettings.Upgrade upgrade() {
        return upgrade;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity)) {
            return InteractionResult.PASS;
        }

        // The same targeting as the pipe screen: the click must land on one pipe's connector.
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0);
        PipeConfigActions.Target target = PipeConfigActions.resolveTarget(entity.bundle(), local.x, local.y, local.z);
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        boolean installed = PipeConfigActions.installEndpointUpgrade(entity, target.pipe(), target.face(), upgrade);
        if (installed && (player == null || !player.hasInfiniteMaterials())) {
            context.getItemInHand().shrink(1);
        }
        if (player != null) {
            Component name = Component.translatable("upgrade.aeroflux." + upgrade.name().toLowerCase(Locale.ROOT));
            Component pipe = Component.translatable("pipe.aeroflux." + target.pipe().id());
            Component face = Component.translatable("face.aeroflux." + target.face().name().toLowerCase(Locale.ROOT));
            String key;
            if (installed) {
                key = "message.aeroflux.endpoint_upgrade_installed";
            } else if (!EndpointSettings.supports(target.pipe(), upgrade)) {
                key = "message.aeroflux.endpoint_upgrade_unsupported";
            } else {
                key = "message.aeroflux.endpoint_upgrade_present";
            }
            player.sendOverlayMessage(Component.translatable(key, name, pipe, face));
        }
        return InteractionResult.SUCCESS;
    }
}
