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
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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

		private static final Identifier UID = Identifier.of(ChestRadar.MOD_ID, "contents");

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
			World world = accessor.getLevel();
			BlockPos pos = accessor.getPosition();
			if (world == null || pos == null) {
				return;
			}
			ContainerRecord record = RadarState.index().at(new ContainerKey(
				world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ()));
			if (record == null) {
				return;
			}

			if (config.showLastSeen) {
				tooltip.add(Text.translatable("chestradar.jade.last_seen", RadarHud.ageText(record))
					.formatted(Formatting.DARK_GRAY));
			}

			ContainerContents.Preview preview = ContainerContents.preview(record,
				RadarState.index().pool(), config.lookAtPreviewRows);
			if (preview.isEmpty()) {
				tooltip.add(Text.translatable("chestradar.hud.empty_container")
					.formatted(Formatting.GRAY));
				return;
			}
			for (ContainerContents.Entry entry : preview.shown()) {
				line(tooltip, entry);
			}
			if (preview.hiddenEntries() > 0) {
				tooltip.add(Text.translatable("chestradar.hud.more", preview.hiddenEntries(),
					CompactCount.format(preview.hiddenCount())).formatted(Formatting.DARK_GRAY));
			}
		}

		private static void line(ITooltip tooltip, ContainerContents.Entry entry) {
			Text label = Text.literal(entry.identity().displayName())
				.append(Text.literal("  ×" + String.format(Locale.ROOT, "%,d", entry.count()))
					.formatted(Formatting.GOLD));
			try {
				tooltip.add(JadeUI.smallItem(ItemIdentityRenderer.toStack(entry.identity(),
					MinecraftClient.getInstance())));
				tooltip.append(label);
			} catch (Exception e) {
				tooltip.add(label);
			}
		}
	}
}
