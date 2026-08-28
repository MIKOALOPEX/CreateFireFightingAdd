package com.mikoalopex.createfirefightingadd.content.items.configurator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleFluidInputHelper;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleParticlePalette;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRule;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MultifunctionConfiguratorScreen extends AbstractContainerScreen<MultifunctionConfiguratorMenu> {
	private static final ResourceLocation BASE_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/multifunction_configurator/base.png");
	private static final ResourceLocation PARTS_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/multifunction_configurator/parts.png");
	private static final ResourceLocation PLAYER_INVENTORY_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/multifunction_configurator/create_player_inventory.png");

	private static final int WIDTH = 280;
	private static final int PLAYER_INVENTORY_WIDTH = 176;
	private static final int PLAYER_INVENTORY_HEIGHT = 108;
	private static final int PLAYER_INVENTORY_X = MultifunctionConfiguratorMenu.PLAYER_INVENTORY_BACKGROUND_X;
	private static final int PLAYER_INVENTORY_Y = MultifunctionConfiguratorMenu.PLAYER_INVENTORY_BACKGROUND_Y;
	private static final int HEIGHT = PLAYER_INVENTORY_Y + PLAYER_INVENTORY_HEIGHT;
	private static final int PARTS_TEXTURE_SIZE = 512;
	private static final AtlasPart TOOL_TAB = new AtlasPart(0, 0, 34, 24);
	private static final AtlasPart NOZZLE_PANEL = new AtlasPart(3, 26, 74, 201);
	private static final AtlasPart FLUID_ENTRY_BUTTON = new AtlasPart(102, 3, 62, 16);
	private static final AtlasPart ENTRY_SCROLL_THUMB = new AtlasPart(80, 56, 6, 20);
	private static final AtlasPart PAGE_BUTTON = new AtlasPart(81, 210, 62, 12);

	private static final int DETAIL_X = 16;
	private static final int DETAIL_Y = 18;
	private static final int DETAIL_TEXTURE_WIDTH = 300;
	private static final int DETAIL_VISIBLE_WIDTH = 264;
	private static final int DETAIL_HEIGHT = 175;
	private static final int DETAIL_CONTENT_X = 104;
	private static final int DETAIL_CONTENT_RIGHT = DETAIL_X + DETAIL_VISIBLE_WIDTH - 16;
	private static final int DETAIL_CONTENT_BOTTOM = DETAIL_Y + DETAIL_HEIGHT - 4;
	private static final int DETAIL_CONTENT_WIDTH = DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_X;
	private static final int DETAIL_CONTENT_SHIFT_Y = -5;
	private static final int DETAIL_CONTENT_SHIFTED_BOTTOM = DETAIL_CONTENT_BOTTOM + DETAIL_CONTENT_SHIFT_Y;

	private static final int FEATURE_X = DETAIL_X - TOOL_TAB.width();
	private static final int FEATURE_Y = DETAIL_Y + 4;
	private static final int FEATURE_VISIBLE_WIDTH = DETAIL_X - FEATURE_X;
	private static final int FEATURE_ROW = 26;
	private static final int NOZZLE_PANEL_X = 22;
	private static final int NOZZLE_PANEL_Y = 13;
	private static final int READ_BUTTON_X = 57;
	private static final int READ_BUTTON_Y = 23;
	private static final int READ_BUTTON_WIDTH = 36;
	private static final int READ_BUTTON_HEIGHT = 18;
	private static final int ENTRY_DELETE_WIDTH = 14;
	private static final int ENTRY_LABEL_OFFSET_X = 16;
	private static final int ENTRY_LABEL_WIDTH = 42;
	private static final int SAVE_BUTTON_X = 28;
	private static final int SAVE_BUTTON_Y = 197;
	private static final int SAVE_BUTTON_WIDTH = 62;
	private static final int SAVE_BUTTON_HEIGHT = 13;
	private static final int PAGE_BUTTON_X = DETAIL_X + DETAIL_VISIBLE_WIDTH - PAGE_BUTTON.width() - 12;
	private static final int PAGE_BUTTON_Y = DETAIL_Y + DETAIL_HEIGHT - PAGE_BUTTON.height() - 6;
	private static final int PAGE_BUTTON_WIDTH = PAGE_BUTTON.width();
	private static final int PAGE_BUTTON_HEIGHT = PAGE_BUTTON.height();

	private static final int LIST_X = NOZZLE_PANEL_X + 13;
	private static final int LIST_Y = 44;
	private static final int LIST_WIDTH = FLUID_ENTRY_BUTTON.width();
	private static final int LIST_SCROLL_X = NOZZLE_PANEL_X + 5;
	private static final int LIST_SCROLL_TOP = 43;
	private static final int LIST_SCROLL_BOTTOM = 177;
	private static final int ENTRY_ROW_HEIGHT = FLUID_ENTRY_BUTTON.height() + 2;
	private static final int ROWS = 7;
	private static final int OPTION_ROW_HEIGHT = 13;

	private static final int EDIT_X = DETAIL_CONTENT_X;
	private static final int EDIT_Y = DETAIL_Y + 16 + DETAIL_CONTENT_SHIFT_Y;
	private static final int EDIT_WIDTH = DETAIL_CONTENT_WIDTH;
	private static final int SEARCH_Y = DETAIL_Y + 34 + DETAIL_CONTENT_SHIFT_Y;
	private static final int PARTICLE_HEADER_Y = DETAIL_Y + 32 + DETAIL_CONTENT_SHIFT_Y;
	private static final int PARTICLE_ROW_Y = DETAIL_Y + 52 + DETAIL_CONTENT_SHIFT_Y;
	private static final int PARTICLE_COLOR_BOX_X = EDIT_X + 40;
	private static final int PARTICLE_COLOR_BOX_WIDTH = 54;
	private static final int PARTICLE_WEIGHT_BOX_X = EDIT_X + 104;
	private static final int PARTICLE_WEIGHT_BOX_WIDTH = 30;
	private static final int OPTION_LIST_Y = DETAIL_Y + 58 + DETAIL_CONTENT_SHIFT_Y;
	private static final int OPTION_ROWS = (DETAIL_CONTENT_SHIFTED_BOTTOM - OPTION_LIST_Y) / OPTION_ROW_HEIGHT;
	private static final int PROCESSING_NONE_Y = OPTION_LIST_Y;
	private static final int PROCESSING_LIST_Y = PROCESSING_NONE_Y + OPTION_ROW_HEIGHT;
	private static final int PROCESSING_ROWS = (DETAIL_CONTENT_SHIFTED_BOTTOM - PROCESSING_LIST_Y) / OPTION_ROW_HEIGHT;
	private static final int FLAG_BUTTON_Y = DETAIL_Y + 52 + DETAIL_CONTENT_SHIFT_Y;
	private static final int FLAG_BUTTON_HEIGHT = 18;

	private Feature selectedFeature = Feature.NOZZLE;
	private NozzleSprayRuleSet nozzleDraft = NozzleSprayRuleSet.EMPTY;
	private int selectedNozzleRule = -1;
	private int entryScroll;
	private int optionScroll;
	private NozzlePage nozzlePage = NozzlePage.TARGET;
	private boolean draggingEntryScroll;
	private Button saveButton;
	private EditBox searchBox;
	private final EditBox[] colorBoxes = new EditBox[3];
	private final EditBox[] weightBoxes = new EditBox[3];

	public MultifunctionConfiguratorScreen(MultifunctionConfiguratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = WIDTH;
		imageHeight = HEIGHT;
	}

	@Override
	protected void init() {
		super.init();
		nozzleDraft = NozzleSprayRuleSet.fromStack(menu.getToolStack(minecraft.player), minecraft.player.registryAccess());
		selectedNozzleRule = nozzleDraft.rules().isEmpty()
			? -1 : Math.clamp(selectedNozzleRule, 0, nozzleDraft.rules().size() - 1);

		searchBox = new EditBox(font, leftPos + EDIT_X, topPos + SEARCH_Y, EDIT_WIDTH, 16,
			Component.translatable("createfirefightingadd.configurator.search"));
		searchBox.setMaxLength(64);
		addRenderableWidget(searchBox);

		saveButton = Button.builder(Component.translatable("createfirefightingadd.configurator.save"), button -> save())
			.bounds(leftPos + SAVE_BUTTON_X, topPos + SAVE_BUTTON_Y, SAVE_BUTTON_WIDTH, SAVE_BUTTON_HEIGHT)
			.build(builder -> new OffsetTextButton(builder, -1));
		addRenderableWidget(saveButton);

		for (int i = 0; i < 3; i++) {
			int y = topPos + PARTICLE_ROW_Y + i * 22;
			colorBoxes[i] = new EditBox(font, leftPos + PARTICLE_COLOR_BOX_X, y, PARTICLE_COLOR_BOX_WIDTH, 16,
				Component.translatable("createfirefightingadd.configurator.nozzle.color"));
			colorBoxes[i].setMaxLength(6);
			addRenderableWidget(colorBoxes[i]);
			weightBoxes[i] = new EditBox(font, leftPos + PARTICLE_WEIGHT_BOX_X, y, PARTICLE_WEIGHT_BOX_WIDTH, 16,
				Component.translatable("createfirefightingadd.configurator.nozzle.weight"));
			weightBoxes[i].setMaxLength(3);
			addRenderableWidget(weightBoxes[i]);
		}
		refreshWidgets();
	}

	private void addRuleFromSlot() {
		if (!addRuleFromContainer(menu.getInputStack()))
			minecraft.player.displayClientMessage(
				Component.translatable("createfirefightingadd.configurator.nozzle.no_fluid").withStyle(ChatFormatting.RED), true);
	}

	boolean addRuleFromContainer(ItemStack stack) {
		return NozzleFluidInputHelper.ruleFromContainer(minecraft.level, stack)
			.map(rule -> {
				addRule(rule);
				return true;
			})
			.orElse(false);
	}

	void addRuleFromFluid(FluidStack fluid) {
		if (!fluid.isEmpty())
			addRule(NozzleFluidInputHelper.ruleFromFluid(minecraft.level, fluid.copyWithAmount(1)));
	}

	Rect2i jeiFluidDropArea() {
		return new Rect2i(leftPos + DETAIL_CONTENT_X, topPos + DETAIL_Y,
			DETAIL_CONTENT_RIGHT - DETAIL_CONTENT_X, DETAIL_CONTENT_BOTTOM - DETAIL_Y);
	}

	private void addRule(NozzleSprayRule rule) {
		nozzleDraft = nozzleDraft.withRule(rule);
		selectedNozzleRule = indexOf(rule.target());
		entryScroll = Math.max(0, selectedNozzleRule - ROWS + 1);
		selectedFeature = Feature.NOZZLE;
		refreshWidgets();
	}

	private void removeSelectedRule() {
		if (!hasSelection())
			return;
		nozzleDraft = nozzleDraft.without(selectedNozzleRule);
		selectedNozzleRule = nozzleDraft.rules().isEmpty() ? -1 : Math.min(selectedNozzleRule, nozzleDraft.rules().size() - 1);
		entryScroll = Math.min(entryScroll, Math.max(0, nozzleDraft.rules().size() - ROWS));
		refreshWidgets();
	}

	private void save() {
		PacketDistributor.sendToServer(new MultifunctionConfiguratorSavePacket(menu.getHand(),
			nozzleDraft.write(minecraft.player.registryAccess())));
	}

	private void toggleFlammable() {
		updateSelected(rule -> rule.withFlags(!rule.flammable(), rule.extinguishing(), rule.igniting()));
	}

	private void toggleExtinguishing() {
		updateSelected(rule -> rule.withFlags(rule.flammable(), !rule.extinguishing(), false));
	}

	private void toggleIgniting() {
		updateSelected(rule -> rule.withFlags(rule.flammable(), false, !rule.igniting()));
	}

	private void updateSelected(java.util.function.UnaryOperator<NozzleSprayRule> updater) {
		if (!canEditSelected())
			return;
		NozzleSprayRule updated = updater.apply(selectedRule());
		nozzleDraft = nozzleDraft.withRule(updated);
		selectedNozzleRule = indexOf(updated.target());
		refreshWidgets();
	}

	private void refreshWidgets() {
		NozzleSprayRule rule = hasSelection() ? selectedRule() : null;
		boolean nozzle = selectedFeature == Feature.NOZZLE;
		boolean hasNozzleRule = nozzle && rule != null;
		boolean editableTarget = nozzle && nozzlePage == NozzlePage.TARGET && rule != null && !rule.locked();
		for (int i = 0; i < 3; i++) {
			EditBox colorBox = colorBoxes[i];
			EditBox weightBox = weightBoxes[i];
			if (colorBox != null) {
				colorBox.visible = nozzle && nozzlePage == NozzlePage.TARGET && rule != null;
				colorBox.setEditable(editableTarget);
				if (rule != null)
					colorBox.setValue(String.format(Locale.ROOT, "%06X", rule.particles().get(i).color()));
			}
			if (weightBox != null) {
				weightBox.visible = nozzle && nozzlePage == NozzlePage.TARGET && rule != null;
				weightBox.setEditable(editableTarget);
				if (rule != null)
					weightBox.setValue(Integer.toString(rule.particles().get(i).weight()));
			}
		}
		if (searchBox != null) {
			searchBox.visible = hasNozzleRule && (nozzlePage == NozzlePage.EFFECTS || nozzlePage == NozzlePage.PROCESSING);
			searchBox.setEditable(searchBox.visible);
		}
		if (saveButton != null) {
			saveButton.visible = nozzle;
			saveButton.active = nozzle;
		}
	}

	private Component flag(String key, boolean enabled) {
		return Component.translatable("createfirefightingadd.configurator.nozzle." + key)
			.append(Component.literal(": "))
			.append(Component.translatable(enabled ? "createfirefightingadd.configurator.on"
				: "createfirefightingadd.configurator.off"));
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		boolean typed = super.charTyped(codePoint, modifiers);
		commitParticleBoxes();
		return typed;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
		commitParticleBoxes();
		return handled;
	}

	private void commitParticleBoxes() {
		if (selectedFeature != Feature.NOZZLE || nozzlePage != NozzlePage.TARGET || !canEditSelected())
			return;
		NozzleSprayRule rule = selectedRule();
		for (int i = 0; i < 3; i++) {
			try {
				rule = rule.withParticleColor(i, Integer.parseInt(colorBoxes[i].getValue(), 16));
			} catch (NumberFormatException ignored) {
			}
		}
		int focusedWeight = focusedWeightBox();
		if (focusedWeight >= 0) {
			try {
				rule = rule.withParticleWeight(focusedWeight, Integer.parseInt(weightBoxes[focusedWeight].getValue()));
			} catch (NumberFormatException ignored) {
			}
		}
		List<NozzleSprayRule> rules = new ArrayList<>(nozzleDraft.rules());
		rules.set(selectedNozzleRule, rule);
		nozzleDraft = new NozzleSprayRuleSet(rules);
		if (focusedWeight >= 0)
			syncUnfocusedWeights(rule, focusedWeight);
	}

	private int focusedWeightBox() {
		for (int i = 0; i < weightBoxes.length; i++) {
			if (weightBoxes[i] != null && weightBoxes[i].isFocused())
				return i;
		}
		return -1;
	}

	private void syncUnfocusedWeights(NozzleSprayRule rule, int focusedWeight) {
		for (int i = 0; i < weightBoxes.length; i++) {
			if (i != focusedWeight && weightBoxes[i] != null)
				weightBoxes[i].setValue(Integer.toString(rule.particles().get(i).weight()));
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		blitPart(graphics, FEATURE_X, FEATURE_Y, TOOL_TAB);
		graphics.blit(BASE_TEXTURE, leftPos + DETAIL_X, topPos + DETAIL_Y, 0, 0,
			DETAIL_TEXTURE_WIDTH, DETAIL_HEIGHT, DETAIL_TEXTURE_WIDTH, DETAIL_HEIGHT);
		blitPart(graphics, NOZZLE_PANEL_X, NOZZLE_PANEL_Y, NOZZLE_PANEL);
		renderEntryScrollThumb(graphics);
		graphics.blit(PLAYER_INVENTORY_TEXTURE, leftPos + PLAYER_INVENTORY_X, topPos + PLAYER_INVENTORY_Y,
			0, 0, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT);
	}

	private void blitPart(GuiGraphics graphics, int x, int y, AtlasPart part) {
		graphics.blit(PARTS_TEXTURE, leftPos + x, topPos + y, part.u(), part.v(), part.width(), part.height(),
			PARTS_TEXTURE_SIZE, PARTS_TEXTURE_SIZE);
	}

	private void blitPartLocal(GuiGraphics graphics, int x, int y, AtlasPart part) {
		graphics.blit(PARTS_TEXTURE, x, y, part.u(), part.v(), part.width(), part.height(),
			PARTS_TEXTURE_SIZE, PARTS_TEXTURE_SIZE);
	}

	private void renderEntryScrollThumb(GuiGraphics graphics) {
		int extraRows = nozzleDraft.rules().size() - ROWS;
		int travel = LIST_SCROLL_BOTTOM - LIST_SCROLL_TOP - ENTRY_SCROLL_THUMB.height();
		int y = extraRows <= 0 ? LIST_SCROLL_TOP : LIST_SCROLL_TOP + Math.round(travel * (entryScroll / (float) extraRows));
		blitPart(graphics, LIST_SCROLL_X, y, ENTRY_SCROLL_THUMB);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		renderFeatureList(graphics);
		if (selectedFeature == Feature.NOZZLE) {
			drawCentered(graphics, Component.translatable("createfirefightingadd.configurator.nozzle.read_short"),
				READ_BUTTON_X - 5, READ_BUTTON_Y + (READ_BUTTON_HEIGHT - font.lineHeight) / 2 + 1, READ_BUTTON_WIDTH,
				0xFFFFFFFF, true);
			if (hasSelection())
				renderPageButton(graphics);
			renderNozzleEntries(graphics, mouseX, mouseY);
			if (hasSelection())
				renderNozzlePage(graphics);
			else
				renderEmptyNozzlePrompt(graphics);
		}
	}

	private void renderPageButton(GuiGraphics graphics) {
		blitPartLocal(graphics, PAGE_BUTTON_X, PAGE_BUTTON_Y, PAGE_BUTTON);
		drawCentered(graphics, Component.literal("<"), PAGE_BUTTON_X, PAGE_BUTTON_Y + 2, PAGE_BUTTON_WIDTH / 2, 0xFFFFFFFF);
		drawCentered(graphics, Component.literal(">"), PAGE_BUTTON_X + PAGE_BUTTON_WIDTH / 2, PAGE_BUTTON_Y + 2,
			PAGE_BUTTON_WIDTH / 2, 0xFFFFFFFF);
	}

	private void renderFeatureList(GuiGraphics graphics) {
		for (Feature feature : Feature.values()) {
			int y = FEATURE_Y + feature.ordinal() * FEATURE_ROW;
			if (feature == Feature.NOZZLE)
				graphics.renderItem(new ItemStack(CreateFireFightingAdd.CONE_NOZZLE_ITEM.get()),
					FEATURE_X + (FEATURE_VISIBLE_WIDTH - 16) / 2, y + (TOOL_TAB.height() - 16) / 2);
			else
				drawCentered(graphics, feature.title(), FEATURE_X, y + (TOOL_TAB.height() - font.lineHeight) / 2,
					FEATURE_VISIBLE_WIDTH, 0xFF3F241B);
		}
	}

	private void drawCentered(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
		drawCentered(graphics, component, x, y, width, color, false);
	}

	private void drawCentered(GuiGraphics graphics, Component component, int x, int y, int width, int color, boolean shadow) {
		Component fitted = fitted(component, width);
		graphics.drawString(font, fitted, x + (width - font.width(fitted)) / 2, y, color, shadow);
	}

	private Component fitted(Component component, int width) {
		String text = component.getString();
		if (font.width(text) <= width)
			return component;
		return Component.literal(font.plainSubstrByWidth(text, width));
	}

	private void renderNozzleEntries(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.enableScissor(leftPos + LIST_X, topPos + LIST_Y,
			leftPos + LIST_X + LIST_WIDTH, topPos + LIST_Y + ROWS * ENTRY_ROW_HEIGHT);
		for (int row = 0; row < ROWS; row++) {
			int index = entryScroll + row;
			if (index >= nozzleDraft.rules().size())
				break;
			int y = LIST_Y + row * ENTRY_ROW_HEIGHT;
			NozzleSprayRule rule = nozzleDraft.rules().get(index);
			blitPartLocal(graphics, LIST_X, y, FLUID_ENTRY_BUTTON);
			Component name = Component.literal(rule.locked() ? "L " : "").append(ruleName(rule));
			renderEntryLabel(graphics, name, index, y, mouseX, mouseY);
		}
		graphics.disableScissor();
	}

	private void renderEntryLabel(GuiGraphics graphics, Component name, int index, int y, int mouseX, int mouseY) {
		int labelX = LIST_X + ENTRY_LABEL_OFFSET_X;
		int labelY = y + 3;
		int color = index == selectedNozzleRule ? 0xFF0F8700 : selectedRuleLocked(index) ? 0xFFFFD080 : 0xFF3F241B;
		boolean hovered = mouseX >= leftPos + labelX && mouseX < leftPos + labelX + ENTRY_LABEL_WIDTH
			&& mouseY >= topPos + y && mouseY < topPos + y + FLUID_ENTRY_BUTTON.height();
		int overflow = font.width(name) - ENTRY_LABEL_WIDTH;
		int offset = hovered && overflow > 0 ? scrollingLabelOffset(overflow) : 0;

		graphics.enableScissor(leftPos + labelX, topPos + y, leftPos + labelX + ENTRY_LABEL_WIDTH,
			topPos + y + FLUID_ENTRY_BUTTON.height());
		graphics.drawString(font, name, labelX - offset, labelY, color, false);
		graphics.disableScissor();
	}

	private boolean selectedRuleLocked(int index) {
		return index >= 0 && index < nozzleDraft.rules().size() && nozzleDraft.rules().get(index).locked();
	}

	private int scrollingLabelOffset(int overflow) {
		int pause = 12;
		int phase = (int) ((Util.getMillis() / 80) % (overflow + pause * 2L));
		if (phase < pause)
			return 0;
		phase -= pause;
		return Math.min(phase, overflow);
	}

	private void renderEntryFluidTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!Screen.hasShiftDown())
			return;
		int index = hoveredEntryIndex(mouseX, mouseY);
		if (index < 0)
			return;
		NozzleSprayRule rule = nozzleDraft.rules().get(index);
		graphics.renderComponentTooltip(font,
			List.of(ruleName(rule), Component.literal(fluidId(rule)).withStyle(ChatFormatting.DARK_GRAY)),
			mouseX, mouseY);
	}

	private int hoveredEntryIndex(int mouseX, int mouseY) {
		if (selectedFeature != Feature.NOZZLE)
			return -1;
		int x = mouseX - leftPos;
		int y = mouseY - topPos;
		if (!inside(x, y, LIST_X, LIST_Y, LIST_WIDTH, ENTRY_ROW_HEIGHT * ROWS))
			return -1;
		if ((y - LIST_Y) % ENTRY_ROW_HEIGHT >= FLUID_ENTRY_BUTTON.height())
			return -1;
		int index = entryScroll + (y - LIST_Y) / ENTRY_ROW_HEIGHT;
		return index >= 0 && index < nozzleDraft.rules().size() ? index : -1;
	}

	private void renderEmptyNozzlePrompt(GuiGraphics graphics) {
		drawCentered(graphics,
			Component.translatable("createfirefightingadd.configurator.nozzle.add_one_fluid")
				.withStyle(ChatFormatting.BOLD),
			EDIT_X, DETAIL_Y + 68 + DETAIL_CONTENT_SHIFT_Y, EDIT_WIDTH, 0xFF000000);
	}

	private void renderNozzlePage(GuiGraphics graphics) {
		if (!hasSelection())
			return;
		NozzleSprayRule rule = selectedRule();
		if (rule.locked())
			graphics.drawString(font, fitted(Component.translatable("createfirefightingadd.configurator.nozzle.locked"), EDIT_WIDTH),
				EDIT_X, EDIT_Y, 0xFF9D2F2F, false);
		if (nozzlePage == NozzlePage.TARGET) {
			renderParticleRows(graphics, rule);
		} else if (nozzlePage == NozzlePage.FLAGS) {
			renderFlags(graphics, rule);
		} else if (nozzlePage == NozzlePage.EFFECTS) {
			renderEffects(graphics, rule);
		} else if (nozzlePage == NozzlePage.PROCESSING) {
			renderProcessing(graphics, rule);
		}
	}

	private void renderPageTitle(GuiGraphics graphics, String key) {
		drawCentered(graphics, Component.translatable(key), EDIT_X, EDIT_Y, EDIT_WIDTH, 0xFF3F241B);
	}

	private void renderParticleRows(GuiGraphics graphics, NozzleSprayRule rule) {
		graphics.drawString(font, Component.translatable("createfirefightingadd.configurator.nozzle.color"),
			PARTICLE_COLOR_BOX_X, PARTICLE_HEADER_Y, 0xFF6A5640, false);
		graphics.drawString(font, Component.translatable("createfirefightingadd.configurator.nozzle.weight"),
			PARTICLE_WEIGHT_BOX_X, PARTICLE_HEADER_Y, 0xFF6A5640, false);
		for (int i = 0; i < 3; i++) {
			NozzleParticlePalette.Entry entry = rule.particles().get(i);
			int y = PARTICLE_ROW_Y + i * 22;
			graphics.fill(EDIT_X, y, EDIT_X + 24, y + 16, 0xFF000000 | entry.color());
			graphics.drawString(font, Component.literal(Integer.toString(i + 1)), EDIT_X + 28, y + 4, 0xFF3F241B, false);
		}
	}

	private void renderFlags(GuiGraphics graphics, NozzleSprayRule rule) {
		renderFlag(graphics, 0, flag("flammable", rule.flammable()), rule.locked());
		renderFlag(graphics, 1, flag("extinguishing", rule.extinguishing()), rule.locked());
		renderFlag(graphics, 2, flag("igniting", rule.igniting()), rule.locked());
	}

	private void renderFlag(GuiGraphics graphics, int index, Component label, boolean locked) {
		int y = FLAG_BUTTON_Y + index * (FLAG_BUTTON_HEIGHT + 6);
		graphics.fill(EDIT_X, y, EDIT_X + EDIT_WIDTH, y + FLAG_BUTTON_HEIGHT, locked ? 0x30505050 : 0x40FFFFFF);
		graphics.drawString(font, fitted(label, EDIT_WIDTH - 8), EDIT_X + 4, y + 5,
			locked ? 0xFF8A7A68 : 0xFF3F241B, false);
	}

	private void renderEffects(GuiGraphics graphics, NozzleSprayRule rule) {
		renderPageTitle(graphics, "createfirefightingadd.configurator.nozzle.title.effects");
		List<ResourceLocation> effects = filteredEffects();
		for (int row = 0; row < OPTION_ROWS && row + optionScroll < effects.size(); row++) {
			ResourceLocation id = effects.get(row + optionScroll);
			int y = OPTION_LIST_Y + row * OPTION_ROW_HEIGHT;
			boolean selected = rule.effects().contains(id);
			graphics.drawString(font, fitted(Component.literal(selected ? "[x] " : "[ ] ").append(effectName(id)), EDIT_WIDTH),
				EDIT_X, y, selected ? 0xFF2E7D32 : 0xFF3F241B, false);
		}
	}

	private void renderProcessing(GuiGraphics graphics, NozzleSprayRule rule) {
		renderPageTitle(graphics, "createfirefightingadd.configurator.nozzle.title.processing");
		List<ResourceLocation> types = filteredProcessingTypes();
		graphics.drawString(font, Component.translatable("createfirefightingadd.configurator.none"),
			EDIT_X, PROCESSING_NONE_Y, rule.fanProcessingType() == null ? 0xFF2E7D32 : 0xFF3F241B, false);
		for (int row = 0; row < PROCESSING_ROWS && row + optionScroll < types.size(); row++) {
			ResourceLocation id = types.get(row + optionScroll);
			int y = PROCESSING_LIST_Y + row * OPTION_ROW_HEIGHT;
			boolean selected = id.equals(rule.fanProcessingType());
			graphics.drawString(font, fitted(Component.literal(selected ? "[x] " : "[ ] ").append(id.toString()), EDIT_WIDTH),
				EDIT_X, y, selected ? 0xFF2E7D32 : 0xFF3F241B, false);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		renderEntryFluidTooltip(graphics, mouseX, mouseY);
		renderFeatureTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && clickEntryScroll(mouseX, mouseY))
			return true;
		if (button == 0 && clickTexturedButton(mouseX, mouseY))
			return true;
		if (button == 0 && clickFeature(mouseX, mouseY))
			return true;
		if (button == 0 && clickNozzleEntry(mouseX, mouseY))
			return true;
		if (button == 0 && clickOption(mouseX, mouseY))
			return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0 && draggingEntryScroll) {
			setEntryScrollFromMouse(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && draggingEntryScroll) {
			draggingEntryScroll = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_WIDTH
			&& mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + ROWS * ENTRY_ROW_HEIGHT) {
			entryScroll = Math.clamp(entryScroll - (int) Math.signum(scrollY), 0,
				Math.max(0, nozzleDraft.rules().size() - ROWS));
			return true;
		}
		if (selectedFeature == Feature.NOZZLE
			&& hasSelection()
			&& mouseX >= leftPos + DETAIL_CONTENT_X
			&& mouseX <= leftPos + DETAIL_CONTENT_RIGHT
			&& mouseY >= topPos + DETAIL_Y && mouseY <= topPos + DETAIL_CONTENT_BOTTOM
			&& (nozzlePage == NozzlePage.EFFECTS || nozzlePage == NozzlePage.PROCESSING)) {
			int max = nozzlePage == NozzlePage.EFFECTS ? filteredEffects().size() : filteredProcessingTypes().size();
			int rows = nozzlePage == NozzlePage.EFFECTS ? OPTION_ROWS : PROCESSING_ROWS;
			optionScroll = Math.clamp(optionScroll - (int) Math.signum(scrollY), 0, Math.max(0, max - rows));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean clickEntryScroll(double mouseX, double mouseY) {
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (!inside(x, y, LIST_SCROLL_X - 2, LIST_SCROLL_TOP, ENTRY_SCROLL_THUMB.width() + 4,
			LIST_SCROLL_BOTTOM - LIST_SCROLL_TOP))
			return false;
		draggingEntryScroll = true;
		setEntryScrollFromMouse(mouseY);
		return true;
	}

	private void setEntryScrollFromMouse(double mouseY) {
		int maxScroll = Math.max(0, nozzleDraft.rules().size() - ROWS);
		if (maxScroll <= 0) {
			entryScroll = 0;
			return;
		}
		int travel = LIST_SCROLL_BOTTOM - LIST_SCROLL_TOP - ENTRY_SCROLL_THUMB.height();
		int localY = (int) mouseY - topPos - LIST_SCROLL_TOP - ENTRY_SCROLL_THUMB.height() / 2;
		entryScroll = Math.clamp(Math.round(maxScroll * (localY / (float) travel)), 0, maxScroll);
	}

	private boolean clickTexturedButton(double mouseX, double mouseY) {
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (inside(x, y, READ_BUTTON_X, READ_BUTTON_Y, READ_BUTTON_WIDTH, READ_BUTTON_HEIGHT)) {
			addRuleFromSlot();
			return true;
		}
		if (hasSelection() && inside(x, y, PAGE_BUTTON_X, PAGE_BUTTON_Y, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)) {
			nozzlePage = x < PAGE_BUTTON_X + PAGE_BUTTON_WIDTH / 2 ? nozzlePage.previous() : nozzlePage.next();
			optionScroll = 0;
			refreshWidgets();
			return true;
		}
		return clickFlagButton(x, y);
	}

	private boolean clickFlagButton(int x, int y) {
		if (selectedFeature != Feature.NOZZLE || nozzlePage != NozzlePage.FLAGS || !canEditSelected())
			return false;
		for (int i = 0; i < 3; i++) {
			int buttonY = FLAG_BUTTON_Y + i * (FLAG_BUTTON_HEIGHT + 6);
			if (!inside(x, y, EDIT_X, buttonY, EDIT_WIDTH, FLAG_BUTTON_HEIGHT))
				continue;
			if (i == 0)
				toggleFlammable();
			else if (i == 1)
				toggleExtinguishing();
			else
				toggleIgniting();
			return true;
		}
		return false;
	}

	private static boolean inside(int x, int y, int areaX, int areaY, int width, int height) {
		return x >= areaX && x < areaX + width && y >= areaY && y < areaY + height;
	}

	private boolean clickFeature(double mouseX, double mouseY) {
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (!inside(x, y, FEATURE_X, FEATURE_Y, FEATURE_VISIBLE_WIDTH, Feature.values().length * FEATURE_ROW))
			return false;
		int index = (y - FEATURE_Y) / FEATURE_ROW;
		selectedFeature = Feature.values()[index];
		refreshWidgets();
		return true;
	}

	private boolean clickNozzleEntry(double mouseX, double mouseY) {
		if (selectedFeature != Feature.NOZZLE)
			return false;
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (!inside(x, y, LIST_X, LIST_Y, LIST_WIDTH, ENTRY_ROW_HEIGHT * ROWS))
			return false;
		int rowY = (y - LIST_Y) % ENTRY_ROW_HEIGHT;
		if (rowY >= FLUID_ENTRY_BUTTON.height())
			return false;
		int index = entryScroll + (y - LIST_Y) / ENTRY_ROW_HEIGHT;
		if (index < 0 || index >= nozzleDraft.rules().size())
			return false;
		if (x - LIST_X < ENTRY_DELETE_WIDTH) {
			selectedNozzleRule = index;
			removeSelectedRule();
			return true;
		}
		selectedNozzleRule = index;
		refreshWidgets();
		return true;
	}

	private boolean clickOption(double mouseX, double mouseY) {
		if (selectedFeature != Feature.NOZZLE || !hasSelection())
			return false;
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (x < EDIT_X || x > EDIT_X + EDIT_WIDTH || selectedRule().locked())
			return false;
		if (nozzlePage == NozzlePage.PROCESSING && inside(x, y, EDIT_X, PROCESSING_NONE_Y - 2, EDIT_WIDTH,
			OPTION_ROW_HEIGHT)) {
			updateSelected(rule -> rule.withFanProcessing(null));
			return true;
		}
		if (nozzlePage == NozzlePage.EFFECTS) {
			if (y < OPTION_LIST_Y || y > OPTION_LIST_Y + OPTION_ROW_HEIGHT * OPTION_ROWS)
				return false;
			int row = (y - OPTION_LIST_Y) / OPTION_ROW_HEIGHT;
			return clickEffect(row);
		}
		if (nozzlePage == NozzlePage.PROCESSING) {
			if (y < PROCESSING_LIST_Y || y > PROCESSING_LIST_Y + OPTION_ROW_HEIGHT * PROCESSING_ROWS)
				return false;
			int row = (y - PROCESSING_LIST_Y) / OPTION_ROW_HEIGHT;
			return clickProcessing(row);
		}
		return false;
	}

	private boolean clickEffect(int row) {
		List<ResourceLocation> effects = filteredEffects();
		int index = optionScroll + row;
		if (index < 0 || index >= effects.size())
			return false;
		ResourceLocation id = effects.get(index);
		List<ResourceLocation> updated = new ArrayList<>(selectedRule().effects());
		if (updated.contains(id))
			updated.remove(id);
		else
			updated.add(id);
		updateSelected(rule -> rule.withEffects(updated));
		return true;
	}

	private boolean clickProcessing(int row) {
		List<ResourceLocation> types = filteredProcessingTypes();
		int index = optionScroll + row;
		if (index < 0 || index >= types.size())
			return false;
		ResourceLocation id = types.get(index);
		updateSelected(rule -> rule.withFanProcessing(id));
		return true;
	}

	private List<ResourceLocation> filteredEffects() {
		String filter = filter();
		return BuiltInRegistries.MOB_EFFECT.keySet().stream()
			.filter(id -> matches(id, effectName(id).getString(), filter))
			.sorted(Comparator.comparing(ResourceLocation::toString))
			.toList();
	}

	private List<ResourceLocation> filteredProcessingTypes() {
		String filter = filter();
		return CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet().stream()
			.filter(id -> {
				FanProcessingType type = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id);
				return type != null && matches(id, id.toString(), filter);
			})
			.sorted(Comparator.comparing(ResourceLocation::toString))
			.toList();
	}

	private boolean matches(ResourceLocation id, String name, String filter) {
		return filter.isBlank()
			|| id.toString().toLowerCase(Locale.ROOT).contains(filter)
			|| name.toLowerCase(Locale.ROOT).contains(filter);
	}

	private String filter() {
		return searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
	}

	private Component effectName(ResourceLocation id) {
		MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
		return effect == null ? Component.literal(id.toString()) : Component.translatable(effect.getDescriptionId());
	}

	private Component ruleName(NozzleSprayRule rule) {
		return rule.target().getHoverName();
	}

	private String fluidId(NozzleSprayRule rule) {
		ResourceLocation id = BuiltInRegistries.FLUID.getKey(rule.target().getFluid());
		return id == null ? "unknown" : id.toString();
	}

	private void renderFeatureTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = mouseX - leftPos;
		int y = mouseY - topPos;
		for (Feature feature : Feature.values()) {
			int featureY = FEATURE_Y + feature.ordinal() * FEATURE_ROW;
			if (!inside(x, y, FEATURE_X, featureY, TOOL_TAB.width(), TOOL_TAB.height()))
				continue;
			graphics.renderComponentTooltip(font, List.of(feature.tooltip()), mouseX, mouseY);
			return;
		}
	}

	private int indexOf(FluidStack target) {
		for (int i = 0; i < nozzleDraft.rules().size(); i++) {
			if (nozzleDraft.rules().get(i).matches(target))
				return i;
		}
		return -1;
	}

	private boolean hasSelection() {
		return selectedNozzleRule >= 0 && selectedNozzleRule < nozzleDraft.rules().size();
	}

	private boolean canEditSelected() {
		return hasSelection() && !selectedRule().locked();
	}

	private NozzleSprayRule selectedRule() {
		return nozzleDraft.rules().get(selectedNozzleRule);
	}

	private static class OffsetTextButton extends Button {
		private final int textYOffset;

		private OffsetTextButton(Builder builder, int textYOffset) {
			super(builder);
			this.textYOffset = textYOffset;
		}

		@Override
		public void renderString(GuiGraphics graphics, Font font, int color) {
			int left = getX() + 2;
			int right = getX() + getWidth() - 2;
			AbstractWidget.renderScrollingString(graphics, font, getMessage(), (left + right) / 2,
				left, getY() + textYOffset, right, getY() + getHeight() + textYOffset, color);
		}

		@Override
		public void setFocused(boolean focused) {
			super.setFocused(false);
		}
	}

	private record AtlasPart(int u, int v, int width, int height) {
	}

	private enum Feature {
		NOZZLE;

		private Component title() {
			return Component.translatable("createfirefightingadd.configurator.feature." + name().toLowerCase(Locale.ROOT));
		}

		private Component tooltip() {
			return Component.translatable("createfirefightingadd.configurator.feature." + name().toLowerCase(Locale.ROOT)
				+ ".tooltip");
		}
	}

	private enum NozzlePage {
		TARGET, FLAGS, EFFECTS, PROCESSING;

		private Component title() {
			return Component.translatable("createfirefightingadd.configurator.nozzle.page." + name().toLowerCase(Locale.ROOT));
		}

		private NozzlePage next() {
			NozzlePage[] values = values();
			return values[(ordinal() + 1) % values.length];
		}

		private NozzlePage previous() {
			NozzlePage[] values = values();
			return values[(ordinal() + values.length - 1) % values.length];
		}
	}
}
