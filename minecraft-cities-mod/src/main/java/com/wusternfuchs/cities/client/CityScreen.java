package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.network.CityViewPayload;
import com.wusternfuchs.cities.network.RequestCityPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The city screen: tabs on the left, the selected page on the right, quick actions at the bottom. Its contents come
 * from the server ({@link CityViewPayload}); actions run the matching {@code /city} command and ask for a refresh.
 */
public class CityScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int TOP = 30;
    private static final int TAB_WIDTH = 96;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 2;
    private static final int LINE_HEIGHT = 10;
    private static final int ACTION_WIDTH = 72;

    private static int lastTab;

    private CityViewPayload view;
    private int tab;
    private int scroll;

    public CityScreen(CityViewPayload view) {
        super(Component.translatable("cities.gui.title"));
        this.view = view;
        this.tab = Math.min(lastTab, CityViewPayload.TABS.size() - 1);
    }

    /** New data from the server: keep the open tab, rebuild the buttons (mayor actions may have changed). */
    public void update(CityViewPayload view) {
        this.view = view;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        List<String> tabs = CityViewPayload.TABS;
        for (int i = 0; i < tabs.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.translatable("cities.gui.tab." + tabs.get(i)), button -> {
                tab = index;
                lastTab = index;
                scroll = 0;
            }).bounds(MARGIN, TOP + i * (TAB_HEIGHT + TAB_GAP), TAB_WIDTH, TAB_HEIGHT).build());
        }

        int x = contentLeft();
        int y = height - TAB_HEIGHT - 6;
        x = action(x, y, "refresh", null);
        if (!view.name().isEmpty()) {
            x = action(x, y, "donate", "city donate");
            x = action(x, y, "rumour", "city rumour");
            if (view.mayor()) {
                x = action(x, y, "festival", "city festival");
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - MARGIN - ACTION_WIDTH, y, ACTION_WIDTH, TAB_HEIGHT).build());
    }

    /** Adds an action button that runs {@code command} (or only refreshes when null) and returns the next x. */
    private int action(int x, int y, String key, String command) {
        addRenderableWidget(Button.builder(Component.translatable("cities.gui.action." + key), button -> {
            if (command != null && minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand(command);
            }
            PacketDistributor.sendToServer(new RequestCityPayload());
        }).bounds(x, y, ACTION_WIDTH, TAB_HEIGHT).build());
        return x + ACTION_WIDTH + 4;
    }

    private int contentLeft() {
        return MARGIN + TAB_WIDTH + MARGIN;
    }

    private List<FormattedCharSequence> wrappedLines(int width) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<List<Component>> pages = view.pages();
        if (tab < pages.size()) {
            for (Component line : pages.get(tab)) {
                lines.addAll(font.split(line, width));
            }
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = contentLeft();
        int width = this.width - left - MARGIN;
        Component heading = view.name().isEmpty()
                ? Component.translatable("cities.gui.title")
                : Component.literal(view.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        graphics.drawString(font, heading, left, 12, 0xFFFFFF);

        List<FormattedCharSequence> lines = wrappedLines(width);
        int bottom = height - TAB_HEIGHT - 12;
        int visible = Math.max(1, (bottom - TOP) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            graphics.drawString(font, lines.get(scroll + i), left, TOP + i * LINE_HEIGHT, 0xFFFFFF);
        }
        if (maxScroll > 0) {
            Component hint = Component.translatable("cities.gui.scroll", scroll + 1, maxScroll + 1)
                    .withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, hint, this.width - MARGIN - font.width(hint), 12, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 3;
        if (scroll < 0) {
            scroll = 0;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
