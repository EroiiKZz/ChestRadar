package fr.imero.chestradar.client.integration;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.config.ChestRadarConfig;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.ContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

import java.util.Locale;

@WailaPlugin
public class JadeIntegration implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(ContentsProvider.INSTANCE, Block.class);
	}

	public enum ContentsProvider implements IBlockComponentProvider {
		INSTANCE;

		private static final Identifier UID = Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "contents");

		private static boolean failureLogged;

		@Override
		public Identifier getUid() {
			return UID;
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			try {
				append(tooltip, accessor);
			} catch (Exception e) {
				if (!failureLogged) {
					failureLogged = true;
					ChestRadar.LOGGER.warn("[chestradar] rendu du tooltip Jade impossible "
						+ "(un seul avertissement)", e);
				}
			}
		}

		private static void append(ITooltip tooltip, BlockAccessor accessor) {
			ChestRadarConfig config = RadarState.config();
			if (!config.lookAtPreview || !config.jadeIntegration) {
				return;
			}
			Level world = accessor.getLevel();
			BlockPos pos = accessor.getPosition();
			if (world == null || pos == null) {
				return;
			}
			ContainerRecord record = RadarState.index().at(new ContainerKey(
				world.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ()));
			if (record == null) {
				return;
			}

			if (config.showLastSeen) {
				tooltip.add(Component.translatable("chestradar.jade.last_seen", RadarHud.ageText(record))
					.withStyle(ChatFormatting.DARK_GRAY));
			}

			ContainerContents.Preview preview = ContainerContents.preview(record,
				RadarState.index().pool(), config.lookAtPreviewRows);
			if (preview.isEmpty()) {
				tooltip.add(Component.translatable("chestradar.hud.empty_container")
					.withStyle(ChatFormatting.GRAY));
				return;
			}
			for (ContainerContents.Entry entry : preview.shown()) {
				line(tooltip, entry);
			}
			if (preview.hiddenEntries() > 0) {
				tooltip.add(Component.translatable("chestradar.hud.more", preview.hiddenEntries(),
					CompactCount.format(preview.hiddenCount())).withStyle(ChatFormatting.DARK_GRAY));
			}
		}

		private static void line(ITooltip tooltip, ContainerContents.Entry entry) {
			Component label = Component.literal(entry.identity().displayName())
				.append(Component.literal("  ×" + String.format(Locale.ROOT, "%,d", entry.count()))
					.withStyle(ChatFormatting.GOLD));
			try {
				tooltip.add(JadeUI.smallItem(ItemIdentityRenderer.toStack(entry.identity(),
					Minecraft.getInstance())));
				tooltip.append(label);
			} catch (Exception e) {
				tooltip.add(label);
			}
		}
	}
}
