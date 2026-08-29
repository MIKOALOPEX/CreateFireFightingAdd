package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.List;
import java.util.Locale;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class FirefighterHandbookScreen extends AbstractContainerScreen<FirefighterHandbookMenu> {
	private static final ResourceLocation BOOK_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/fire_handbook/book.png");
	private static final ResourceLocation RIBBON_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/fire_handbook/ribbon.png");
	private static final ResourceLocation MEDAL_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/fire_handbook/medal.png");
	private static final ResourceLocation PEN_TEXTURE =
		CreateFireFightingAdd.path("textures/gui/fire_handbook/pen.png");
	private static final ResourceLocation FIRE_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/block/fire_0.png");
	private static final ResourceLocation PAGE_FORWARD =
		ResourceLocation.withDefaultNamespace("widget/page_forward");
	private static final ResourceLocation PAGE_FORWARD_HIGHLIGHTED =
		ResourceLocation.withDefaultNamespace("widget/page_forward_highlighted");
	private static final ResourceLocation PAGE_BACKWARD =
		ResourceLocation.withDefaultNamespace("widget/page_backward");
	private static final ResourceLocation PAGE_BACKWARD_HIGHLIGHTED =
		ResourceLocation.withDefaultNamespace("widget/page_backward_highlighted");
	private static final int TEXT_COLOR = 0xFF574435;
	private static final int IMAGE_WIDTH = 334;
	private static final int IMAGE_HEIGHT = 244;
	private static final int BOOK_X = 32;
	private static final int BOOK_Y = 32;
	private static final int BOOK_W = 270;
	private static final int BOOK_H = 180;
	private static final int RIBBON_X = 433;
	private static final int RIBBON_Y = 32;
	private static final int MEDAL_X = 348;
	private static final int MEDAL_Y = 28;
	private static final int PEN_W = 12;
	private static final int PEN_H = 164;
	private static final int LEFT_PAGE_X = BOOK_X + 34;
	private static final int LEFT_PAGE_Y = BOOK_Y + 28;
	private static final int LEFT_PAGE_W = 95;
	private static final int RIGHT_PAGE_X = BOOK_X + 145;
	private static final int RIGHT_PAGE_Y = BOOK_Y + 28;
	private static final int RIGHT_PAGE_W = 96;
	private static final int PAGE_BOTTOM_Y = BOOK_Y + BOOK_H - 28;
	private static final int PAGE_COUNT = 2;
	private static final int FIRE_FRAME_SIZE = 16;
	private static final int FIRE_TEXTURE_HEIGHT = 512;
	private static final int MEDAL_CLOSED_X = MEDAL_X - 122;
	private static final int RIBBON_CLOSED_X = RIBBON_X - 187;
	private static final int MEDAL_OPEN_OFFSET = 80;
	private static final int CAREER_READY_OFFSET_X = -20;
	private static final int CAREER_PROGRESS_OFFSET_X = -40;
	private static final int CAREER_TITLE_X = LEFT_PAGE_X + CAREER_PROGRESS_OFFSET_X;
	private static final int CAREER_TITLE_W = LEFT_PAGE_W + 56;
	private static final int READY_TEXT_X = LEFT_PAGE_X + CAREER_READY_OFFSET_X + 14;
	private static final int READY_TEXT_W = 94;
	private static final int TEAM_SEARCH_X = RIGHT_PAGE_X + 10;
	private static final int TEAM_SEARCH_Y = RIGHT_PAGE_Y + 34;
	private static final int TEAM_SEARCH_W = RIGHT_PAGE_W - 20;
	private static final int TEAM_SEARCH_LIST_Y = TEAM_SEARCH_Y + 22;
	private static final int TEAM_SEARCH_LIST_H = 70;
	private static final int TEAM_LIST_PANEL_COLOR = 0x33CCB998;
	private static final int TEAM_LIST_OUTLINE_COLOR = 0x66CCB998;
	private static final int TEAM_MEMBER_ROW_Y = LEFT_PAGE_Y + 42;
	private static final int TEAM_MEMBER_ROW_H = 11;
	private static final int SEARCH_TEXT_X_OFFSET = 6;
	private static final int SEARCH_TEXT_Y_OFFSET = 5;
	private static final int SEARCH_TEXT_RIGHT_PADDING = 8;
	private static final String SEARCH_RESERVED_CHARACTER = "W";

	private FirefighterHandbookSnapshot snapshot;
	private int page;
	private int penX;
	private int penY;
	private int penDragX;
	private int penDragY;
	private boolean draggingPen;
	private final boolean completedWhenOpened;
	private boolean medalExpanded;
	private float medalSlide;
	private int inviteScroll;
	private EditBox searchBox;

	public FirefighterHandbookScreen(FirefighterHandbookMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = IMAGE_WIDTH;
		imageHeight = IMAGE_HEIGHT;
		snapshot = menu.snapshot();
		completedWhenOpened = snapshot.hasMedal();
		penX = BOOK_X + (BOOK_W - PEN_W) / 2;
		penY = BOOK_Y + (BOOK_H - PEN_H) / 2;
	}

	public static void applySnapshot(FirefighterHandbookSnapshot snapshot) {
		if (net.minecraft.client.Minecraft.getInstance().screen instanceof FirefighterHandbookScreen screen)
			screen.updateSnapshot(snapshot);
	}

	private void updateSnapshot(FirefighterHandbookSnapshot snapshot) {
		this.snapshot = snapshot;
		menu.setSnapshot(snapshot);
		clampInviteScroll();
		refreshWidgets();
	}

	@Override
	protected void init() {
		super.init();
		refreshWidgets();
	}

	private void refreshWidgets() {
		clearWidgets();
		if (page > 0 && !snapshot.registered())
			page = 0;
		Button previous = Button.builder(Component.empty(), button -> changePage(-1))
			.bounds(leftPos + LEFT_PAGE_X, topPos + PAGE_BOTTOM_Y, 23, 13)
			.build(builder -> new BookPageButton(builder, false));
		previous.active = snapshot.registered() && page > 0;
		addRenderableWidget(previous);
		Button next = Button.builder(Component.empty(), button -> changePage(1))
			.bounds(leftPos + RIGHT_PAGE_X + RIGHT_PAGE_W - 23, topPos + PAGE_BOTTOM_Y, 23, 13)
			.build(builder -> new BookPageButton(builder, true));
		next.active = snapshot.registered() && page < PAGE_COUNT - 1;
		addRenderableWidget(next);
		if (page == 0)
			addCareerWidgets();
		else
			addTeamWidgets();
	}

	private void addCareerWidgets() {
		if (!snapshot.registered()) {
			addRenderableWidget(Button.builder(
				Component.translatable("createfirefightingadd.firefighter_handbook.ready_button"),
				button -> send(FirefighterHandbookActionPacket.Action.REGISTER, ""))
				.bounds(leftPos + READY_TEXT_X, topPos + LEFT_PAGE_Y + 78, READY_TEXT_W, 20)
				.build(builder -> new SearchToneButton(builder)));
			return;
		}
		if (!snapshot.serverRecordsEnabled()) {
			int y = topPos + LEFT_PAGE_Y + 92;
			int x = leftPos + LEFT_PAGE_X + CAREER_PROGRESS_OFFSET_X + 20;
			addRenderableWidget(Button.builder(Component.literal("Fill up"),
				button -> send(FirefighterHandbookActionPacket.Action.MANUAL_FILL, ""))
				.bounds(x + 7, y, 53, 18).build(builder -> new SearchToneButton(builder)));
			addRenderableWidget(Button.builder(Component.literal("+"),
				button -> send(FirefighterHandbookActionPacket.Action.MANUAL_ADD_1, ""))
				.bounds(x + 64, y, 17, 18).build(builder -> new SearchToneButton(builder)));
			addRenderableWidget(Button.builder(Component.literal("-"),
				button -> send(FirefighterHandbookActionPacket.Action.MANUAL_SUB_1, ""))
				.bounds(x + 85, y, 17, 18).build(builder -> new SearchToneButton(builder)));
		}
	}

	private void addTeamWidgets() {
		searchBox = new TransparentSearchBox(font, leftPos + TEAM_SEARCH_X, topPos + TEAM_SEARCH_Y, TEAM_SEARCH_W, 16,
			Component.translatable("createfirefightingadd.firefighter_handbook.search"));
		searchBox.setMaxLength(32);
		searchBox.setResponder(value -> {
			inviteScroll = 0;
			clampInviteScroll();
		});
		addRenderableWidget(searchBox);
	}

	private void changePage(int delta) {
		if (!snapshot.registered())
			return;
		page = Mth.clamp(page + delta, 0, PAGE_COUNT - 1);
		refreshWidgets();
	}

	private void send(FirefighterHandbookActionPacket.Action action, String target) {
		PacketDistributor.sendToServer(new FirefighterHandbookActionPacket(menu.hand(), action, target));
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		if (shouldShowMedal())
			graphics.blit(RIBBON_TEXTURE, leftPos + ribbonX(), topPos + RIBBON_Y, 0, 0, 30, 208, 30, 208);
		graphics.blit(BOOK_TEXTURE, leftPos + BOOK_X, topPos + BOOK_Y, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
		if (shouldShowMedal())
			graphics.blit(MEDAL_TEXTURE, leftPos + medalX(), topPos + MEDAL_Y, 0, 0, 60, 95, 60, 95);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		if (page == 0)
			renderCareer(graphics);
		else
			renderTeam(graphics, mouseX, mouseY);
	}

	private void renderCareer(GuiGraphics graphics) {
		drawScaledCentered(graphics, Component.translatable("createfirefightingadd.firefighter_handbook.career")
			.withStyle(ChatFormatting.BOLD), CAREER_TITLE_X, LEFT_PAGE_Y + 4, CAREER_TITLE_W, 1.65f);
		if (!snapshot.registered()) {
			drawWrapped(graphics, Component.translatable("createfirefightingadd.firefighter_handbook.ready_prompt"),
				READY_TEXT_X, LEFT_PAGE_Y + 45, READY_TEXT_W);
			return;
		}
		renderFire(graphics, LEFT_PAGE_X + CAREER_PROGRESS_OFFSET_X + 38, LEFT_PAGE_Y + 28);
		drawScaledCentered(graphics, Component.translatable("createfirefightingadd.firefighter_handbook.count",
			snapshot.extinguished(), FirefighterHandbookSnapshot.GOAL), LEFT_PAGE_X + CAREER_PROGRESS_OFFSET_X - 10,
			LEFT_PAGE_Y + 70, LEFT_PAGE_W + 72, 0.75f);
		if (snapshot.extinguished() >= FirefighterHandbookSnapshot.GOAL && !completedWhenOpened)
			drawWrapped(graphics, Component.translatable("createfirefightingadd.firefighter_handbook.reopen"),
				LEFT_PAGE_X + CAREER_PROGRESS_OFFSET_X + 18, LEFT_PAGE_Y + 115, LEFT_PAGE_W + 28);
	}

	private void renderTeam(GuiGraphics graphics, int mouseX, int mouseY) {
		drawScaledCentered(graphics, Component.translatable("createfirefightingadd.firefighter_handbook.team")
			.withStyle(ChatFormatting.BOLD), CAREER_TITLE_X, LEFT_PAGE_Y + 4, CAREER_TITLE_W, 1.65f);
		int y = TEAM_MEMBER_ROW_Y;
		graphics.drawString(font, Component.translatable("createfirefightingadd.firefighter_handbook.members"),
			LEFT_PAGE_X, LEFT_PAGE_Y + 28, TEXT_COLOR, false);
		for (FirefighterHandbookSnapshot.PlayerEntry entry : snapshot.teamMembers()) {
			int nameWidth = snapshot.teamCaptain() && !entry.id().equals(snapshot.owner()) ? LEFT_PAGE_W - 14 : LEFT_PAGE_W;
			graphics.drawString(font, fitted(Component.literal(entry.name()), nameWidth),
				LEFT_PAGE_X, y, TEXT_COLOR, false);
			if (snapshot.teamCaptain() && !entry.id().equals(snapshot.owner()))
				graphics.drawString(font, "X", LEFT_PAGE_X + LEFT_PAGE_W - 8, y, 0xFFFF3333, false);
			y += TEAM_MEMBER_ROW_H;
		}
		graphics.drawString(font, Component.translatable("createfirefightingadd.firefighter_handbook.search"),
			TEAM_SEARCH_X, RIGHT_PAGE_Y + 12, TEXT_COLOR, false);
		graphics.fill(TEAM_SEARCH_X, TEAM_SEARCH_LIST_Y, TEAM_SEARCH_X + TEAM_SEARCH_W,
			TEAM_SEARCH_LIST_Y + TEAM_SEARCH_LIST_H, TEAM_LIST_PANEL_COLOR);
		graphics.renderOutline(TEAM_SEARCH_X, TEAM_SEARCH_LIST_Y, TEAM_SEARCH_W, TEAM_SEARCH_LIST_H,
			TEAM_LIST_OUTLINE_COLOR);
		renderInviteList(graphics, mouseX, mouseY);
	}

	private void renderInviteList(GuiGraphics graphics, int mouseX, int mouseY) {
		List<FirefighterHandbookSnapshot.PlayerEntry> players = filteredInvitePlayers();
		clampInviteScroll(players.size());
		int y = TEAM_SEARCH_LIST_Y + 3;
		int visibleRows = visibleInviteRows();
		graphics.enableScissor(leftPos + TEAM_SEARCH_X + 1, topPos + TEAM_SEARCH_LIST_Y + 1,
			leftPos + TEAM_SEARCH_X + TEAM_SEARCH_W - 1, topPos + TEAM_SEARCH_LIST_Y + TEAM_SEARCH_LIST_H - 1);
		for (FirefighterHandbookSnapshot.PlayerEntry player : players.stream().skip(inviteScroll).limit(visibleRows).toList()) {
			boolean hovered = mouseX - leftPos >= TEAM_SEARCH_X + 2 && mouseX - leftPos < TEAM_SEARCH_X + TEAM_SEARCH_W - 2
				&& mouseY - topPos >= y && mouseY - topPos < y + 10;
			renderScrollingText(graphics, player.name(), TEAM_SEARCH_X + 3, y, TEAM_SEARCH_W - 6, hovered);
			y += 11;
		}
		graphics.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (button == 0 && shouldShowMedal() && x >= medalX() && x < medalX() + 60
			&& y >= MEDAL_Y && y < MEDAL_Y + 95) {
			medalExpanded = !medalExpanded;
			return true;
		}
		if (button == 0 && x >= penX && x < penX + PEN_W && y >= penY && y < penY + PEN_H) {
			draggingPen = true;
			penDragX = x - penX;
			penDragY = y - penY;
			return true;
		}
		if (button == 0 && page == 1 && clickKickMember(x, y))
			return true;
		if (button == 0 && page == 1 && clickInvite(x, y))
			return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickInvite(int x, int y) {
		if (x < TEAM_SEARCH_X || x >= TEAM_SEARCH_X + TEAM_SEARCH_W
			|| y < TEAM_SEARCH_LIST_Y || y >= TEAM_SEARCH_LIST_Y + TEAM_SEARCH_LIST_H)
			return false;
		List<FirefighterHandbookSnapshot.PlayerEntry> players = filteredInvitePlayers();
		clampInviteScroll(players.size());
		int row = (y - (TEAM_SEARCH_LIST_Y + 3)) / 11 + inviteScroll;
		if (row < 0 || row >= players.size())
			return false;
		send(FirefighterHandbookActionPacket.Action.INVITE, players.get(row).id().toString());
		return true;
	}

	private boolean clickKickMember(int x, int y) {
		if (!snapshot.teamCaptain() || x < LEFT_PAGE_X + LEFT_PAGE_W - 12 || x >= LEFT_PAGE_X + LEFT_PAGE_W
			|| y < TEAM_MEMBER_ROW_Y)
			return false;
		int row = (y - TEAM_MEMBER_ROW_Y) / TEAM_MEMBER_ROW_H;
		if (row < 0 || row >= snapshot.teamMembers().size())
			return false;
		FirefighterHandbookSnapshot.PlayerEntry member = snapshot.teamMembers().get(row);
		if (member.id().equals(snapshot.owner()))
			return false;
		send(FirefighterHandbookActionPacket.Action.KICK_MEMBER, member.id().toString());
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0 && draggingPen) {
			penX = Math.clamp((int) mouseX - leftPos - penDragX, 0, imageWidth - PEN_W);
			penY = Math.clamp((int) mouseY - topPos - penDragY, 0, imageHeight - PEN_H);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int x = (int) mouseX - leftPos;
		int y = (int) mouseY - topPos;
		if (page == 1 && x >= TEAM_SEARCH_X && x < TEAM_SEARCH_X + TEAM_SEARCH_W
			&& y >= TEAM_SEARCH_LIST_Y && y < TEAM_SEARCH_LIST_Y + TEAM_SEARCH_LIST_H) {
			inviteScroll += scrollY > 0 ? -1 : 1;
			clampInviteScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox != null && searchBox.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE)
				return super.keyPressed(keyCode, scanCode, modifiers);
			searchBox.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers))
			return true;
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && draggingPen) {
			draggingPen = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		tickMedalSlide();
		renderPageNumber(graphics);
		renderPen(graphics);
		renderTooltip(graphics, mouseX, mouseY);
	}

	private void drawScaledCentered(GuiGraphics graphics, Component component, int x, int y, int width, float scale) {
		Component fitted = fitted(component, Math.round(width / scale));
		int drawX = Math.round((x + (width - font.width(fitted) * scale) / 2) / scale);
		int drawY = Math.round(y / scale);
		graphics.pose().pushPose();
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, fitted, drawX, drawY, TEXT_COLOR, false);
		graphics.pose().popPose();
	}

	private void renderFire(GuiGraphics graphics, int x, int y) {
		int frame = (int) ((System.currentTimeMillis() / 80L) % (FIRE_TEXTURE_HEIGHT / FIRE_FRAME_SIZE));
		graphics.blit(FIRE_TEXTURE, x, y, 24, 24, 0, frame * FIRE_FRAME_SIZE, FIRE_FRAME_SIZE, FIRE_FRAME_SIZE,
			FIRE_FRAME_SIZE, FIRE_TEXTURE_HEIGHT);
	}

	private boolean shouldShowMedal() {
		return page == 0 && snapshot.hasMedal() && completedWhenOpened;
	}

	private void tickMedalSlide() {
		float target = medalExpanded ? 1 : 0;
		medalSlide += (target - medalSlide) * 0.25f;
	}

	private int medalX() {
		return MEDAL_CLOSED_X + Math.round(MEDAL_OPEN_OFFSET * medalSlide);
	}

	private int ribbonX() {
		return RIBBON_CLOSED_X + Math.round(MEDAL_OPEN_OFFSET * medalSlide);
	}

	private void renderPageNumber(GuiGraphics graphics) {
		Component pageText = Component.literal((page + 1) + "/" + PAGE_COUNT);
		graphics.drawString(font, pageText, leftPos + BOOK_X + (BOOK_W - font.width(pageText)) / 2,
			topPos + BOOK_Y + BOOK_H - 18, TEXT_COLOR, false);
	}

	private void renderPen(GuiGraphics graphics) {
		graphics.blit(PEN_TEXTURE, leftPos + penX, topPos + penY, 0, 0, PEN_W, PEN_H, PEN_W, PEN_H);
	}

	private void drawWrapped(GuiGraphics graphics, Component component, int x, int y, int width) {
		for (var line : font.split(component, width)) {
			graphics.drawString(font, line, x, y, TEXT_COLOR, false);
			y += font.lineHeight + 2;
		}
	}

	private Component fitted(Component component, int width) {
		String text = component.getString();
		if (font.width(text) <= width)
			return component;
		return Component.literal(font.plainSubstrByWidth(text, width));
	}

	private void renderScrollingText(GuiGraphics graphics, String text, int x, int y, int width, boolean hovered) {
		int color = hovered ? 0xFF0F8700 : TEXT_COLOR;
		int textWidth = font.width(text);
		if (!hovered || textWidth <= width) {
			graphics.drawString(font, fitted(Component.literal(text), width), x, y, color, false);
			return;
		}
		int overflow = textWidth - width;
		int offset = (int) ((System.currentTimeMillis() / 120L) % (overflow + 18));
		if (offset > overflow)
			offset = overflow - (offset - overflow);
		graphics.drawString(font, text, x - offset, y, color, false);
	}

	private int visibleInviteRows() {
		return Math.max(1, (TEAM_SEARCH_LIST_H - 6) / 11);
	}

	private List<FirefighterHandbookSnapshot.PlayerEntry> filteredInvitePlayers() {
		String filter = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		return snapshot.registeredPlayers().stream()
			.filter(player -> !player.id().equals(snapshot.owner()))
			.filter(player -> filter.isBlank() || player.name().toLowerCase(Locale.ROOT).contains(filter))
			.toList();
	}

	private void clampInviteScroll() {
		clampInviteScroll(filteredInvitePlayers().size());
	}

	private void clampInviteScroll(int playerCount) {
		inviteScroll = Mth.clamp(inviteScroll, 0, Math.max(0, playerCount - visibleInviteRows()));
	}

	private static class HandbookTextButton extends Button {
		HandbookTextButton(Builder builder) {
			super(builder);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int background = isHoveredOrFocused() ? 0xFFD8C8AA : 0xFFCCB998;
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
			graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF2D2116);
			renderString(graphics, net.minecraft.client.Minecraft.getInstance().font, TEXT_COLOR);
		}

		@Override
		public void renderString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int color) {
			Component fitted = getMessage();
			int x = getX() + (getWidth() - font.width(fitted)) / 2;
			graphics.drawString(font, fitted, x, getY() + 6, color, false);
		}
	}

	private static class SearchToneButton extends HandbookTextButton {
		SearchToneButton(Builder builder) {
			super(builder);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int background = isHoveredOrFocused() ? 0x55CCB998 : TEAM_LIST_PANEL_COLOR;
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
			graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), TEAM_LIST_OUTLINE_COLOR);
			renderString(graphics, net.minecraft.client.Minecraft.getInstance().font, TEXT_COLOR);
		}
	}

	private static class BookPageButton extends Button {
		private final boolean forward;

		BookPageButton(Builder builder, boolean forward) {
			super(builder);
			this.forward = forward;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			ResourceLocation sprite = forward
				? (isHoveredOrFocused() ? PAGE_FORWARD_HIGHLIGHTED : PAGE_FORWARD)
				: (isHoveredOrFocused() ? PAGE_BACKWARD_HIGHLIGHTED : PAGE_BACKWARD);
			graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
		}
	}

	private static class TransparentSearchBox extends EditBox {
		private final net.minecraft.client.gui.Font font;

		TransparentSearchBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
			super(font, x, y, width, height, message);
			this.font = font;
			setBordered(false);
			setTextColor(TEXT_COLOR);
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), TEAM_LIST_PANEL_COLOR);
			graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), TEAM_LIST_OUTLINE_COLOR);
			String value = getValue();
			int textX = getX() + SEARCH_TEXT_X_OFFSET;
			int textY = getY() + SEARCH_TEXT_Y_OFFSET;
			int maxWidth = getWidth() - SEARCH_TEXT_RIGHT_PADDING - font.width(SEARCH_RESERVED_CHARACTER);
			while (font.width(value) > maxWidth && !value.isEmpty())
				value = value.substring(1);
			graphics.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
			graphics.drawString(font, value, textX, textY, TEXT_COLOR, false);
			if (isFocused() && (System.currentTimeMillis() / 300L) % 2L == 0) {
				int cursorX = Math.min(textX + font.width(value), getX() + getWidth() - 4);
				graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 9, TEXT_COLOR);
			}
			graphics.disableScissor();
		}
	}
}
