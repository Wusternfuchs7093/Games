package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.network.BoardPayload;
import com.wusternfuchs.cities.network.RequestBoardPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The job board as its own screen: every job with a button to hand in the goods or take the hunt. Opened with
 * the board key (J), by right-clicking a job board block or from the city screen. Buttons run the matching
 * {@code /city board} command and ask the server for a fresh board.
 */
public class JobBoardScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int TOP = 34;
    private static final int ROW = 24;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 20;

    private BoardPayload board;
    private int scroll;

    public JobBoardScreen(BoardPayload board) {
        super(Component.translatable("cities.gui.board.title"));
        this.board = board;
    }

    public void update(BoardPayload board) {
        this.board = board;
        rebuildWidgets();
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - 60) / ROW);
    }

    @Override
    protected void init() {
        List<BoardPayload.Entry> entries = board.entries();
        scroll = Math.max(0, Math.min(scroll, entries.size() - visibleRows()));
        int x = width - MARGIN - BUTTON_WIDTH;
        for (int i = 0; i < visibleRows() && scroll + i < entries.size(); i++) {
            BoardPayload.Entry entry = entries.get(scroll + i);
            int y = TOP + i * ROW;
            Button button = switch (entry.kind()) {
                case BoardPayload.DELIVER -> action(x, y, Component.translatable("cities.gui.board.deliver", entry.have()),
                        "city board deliver " + entry.id());
                case BoardPayload.HUNT -> switch (entry.state()) {
                    case BoardPayload.MINE -> action(x, y, Component.translatable("cities.gui.board.yours"), null);
                    case BoardPayload.TAKEN -> action(x, y, Component.translatable("cities.gui.board.taken"), null);
                    default -> action(x, y, Component.translatable("cities.gui.board.take"), "city board take " + entry.id());
                };
                default -> null;
            };
            if (button != null) {
                button.active = entry.kind() == BoardPayload.DELIVER ? entry.have() > 0
                        : entry.kind() == BoardPayload.HUNT && entry.state() == BoardPayload.FREE;
                addRenderableWidget(button);
            }
        }
        int bottom = height - BUTTON_HEIGHT - 8;
        addRenderableWidget(Button.builder(Component.translatable("cities.gui.action.refresh"),
                button -> PacketDistributor.sendToServer(new RequestBoardPayload()))
                .bounds(MARGIN, bottom, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - MARGIN - BUTTON_WIDTH, bottom, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** A row button that runs {@code command} and refreshes the board; null makes a label-only button. */
    private Button action(int x, int y, Component label, String command) {
        return Button.builder(label, button -> {
            if (command != null && minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand(command);
                PacketDistributor.sendToServer(new RequestBoardPayload());
            }
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component heading = board.name().isEmpty()
                ? Component.translatable("cities.gui.board.title")
                : Component.translatable("cities.gui.board.heading", board.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        graphics.drawCenteredString(font, heading, width / 2, 12, 0xFFFFFF);

        List<BoardPayload.Entry> entries = board.entries();
        int textWidth = width - MARGIN * 3 - BUTTON_WIDTH;
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("cities.board.empty").withStyle(ChatFormatting.GRAY),
                    MARGIN, TOP + 6, 0xFFFFFF);
        }
        for (int i = 0; i < visibleRows() && scroll + i < entries.size(); i++) {
            BoardPayload.Entry entry = entries.get(scroll + i);
            int y = TOP + i * ROW;
            List<FormattedCharSequence> lines = font.split(entry.text(), textWidth);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(font, lines.get(line), MARGIN, y + 2 + line * 10, 0xFFFFFF);
            }
        }
        int noteY = TOP + Math.min(visibleRows(), Math.max(1, entries.size())) * ROW + 4;
        for (Component note : board.notes()) {
            for (FormattedCharSequence line : font.split(note, width - MARGIN * 2)) {
                if (noteY > height - BUTTON_HEIGHT - 20) {
                    return;
                }
                graphics.drawString(font, line, MARGIN, noteY, 0xA0A0A0);
                noteY += 10;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int before = scroll;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), board.entries().size() - visibleRows()));
        if (scroll != before) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
