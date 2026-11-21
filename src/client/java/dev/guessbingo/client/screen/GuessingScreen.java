package dev.guessbingo.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.client.DisplayGroup;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.lex.LexiconReader;
import dev.guessbingo.lex.Question;
import dev.guessbingo.net.GuessData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class GuessingScreen extends Screen {
    private static final Identifier QUESTION_SEARCH = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "textures/gui/question_search.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final int IMAGE_WIDTH = 195;
    private static final int IMAGE_HEIGHT = 136;
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int SCROLLBAR_X = 175;
    private static final int SCROLLBAR_Y = 18;
    private static final int SCROLLBAR_HEIGHT = 112;

    private EditBox searchField;
    private Button searchButton;
    private int leftPos;
    private int topPos;
    private int selectedIndex = -1;
    private final List<DisplayGroup> displayGroups = new ArrayList<>();
    private float scrollOffs;
    private boolean scrolling;

    public GuessingScreen() {
        this(Component.literal("Question Search"));
    }

    protected GuessingScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;
        this.searchField = new EditBox(this.font, this.leftPos + 81, this.topPos + 5, 88, 12, Component.literal("Search"));
        this.searchField.setMaxLength(64);
        this.searchField.setBordered(false);
        this.searchField.setTextColor(-1);
        this.searchField.setCanLoseFocus(false);
        this.searchField.setFocused(true);
        this.setInitialFocus(this.searchField);
        this.addWidget(this.searchField);
        this.searchButton = Button.builder(Component.literal("GO"), button -> this.search())
                .pos(this.leftPos + 172, this.topPos + 6)
                .size(18, 12)
                .build();
        this.addWidget(this.searchButton);
    }

    /** 取消打开屏幕时对背后世界的背景模糊。 */
    @Override
    protected void extractBlurredBackground(@NonNull GuiGraphicsExtractor graphics) {
    }

    private void search() {
        String query = this.searchField.getValue().trim().toLowerCase(Locale.ROOT);
        this.displayGroups.clear();
        for (LexiconReader.ClassifiedQuestions classified : LexiconReader.getQuestions().values()) {
            for (Question question : classified.all()) {
                DisplayGroup group = DisplayGroup.fromQuestion(question);
                if (group.components().getFirst().getString().contains(query) || question.id().toString().contains(query)) {
                    this.displayGroups.add(group);
                }
            }
        }
        this.scrollOffs = 0.0F;
    }

    @Override
    public void extractMenuBackground(@NonNull GuiGraphicsExtractor graphics) {
        super.extractMenuBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, QUESTION_SEARCH, this.leftPos, this.topPos, 0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.searchField.extractRenderState(graphics, mouseX, mouseY, a);
        this.searchButton.extractRenderState(graphics, mouseX, mouseY, a);
        int size = this.displayGroups.size();
        int first = getRowIndexForScroll(this.scrollOffs) * COLUMNS;
        int selected = -1;
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int index = first + i;

            if (index >= size) break;
            DisplayGroup group = this.displayGroups.get(index);
            int x = this.leftPos + 9 + (i % COLUMNS) * 18;
            int y = this.topPos + 18 + (i / COLUMNS) * 18;
            graphics.item(group.itemStack(), x, y);
            if (isInsideSlot(x, y, mouseX, mouseY)) {
                selected = index;
                graphics.setTooltipForNextFrame(this.font, this.displayGroups.get(index).components(), Optional.<TooltipComponent>empty(), mouseX, mouseY);
            }
        }
        this.selectedIndex = selected;
        int scrollbarX = this.leftPos + SCROLLBAR_X;
        int scrollbarY = this.topPos + SCROLLBAR_Y;
        boolean canScroll = canScroll();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, canScroll ? SCROLLER : SCROLLER_DISABLED, scrollbarX, scrollbarY + (int) (95.0F * this.scrollOffs), 12, 15);
        if (isInsideScrollbar(mouseX, mouseY)) {
            graphics.requestCursor(canScroll ? CursorTypes.RESIZE_NS : CursorTypes.NOT_ALLOWED);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (isInsideScrollbar(event.x(), event.y())) {
            this.scrolling = this.canScroll();
            return true;
        }
        else if(this.selectedIndex != -1){
            Question question = this.displayGroups.get(selectedIndex).question();
            LocalPlayer player = Minecraft.getInstance().player;
            if(question != null && player != null)
                ClientPlayNetworking.send(new GuessData(player.getAttachedOrElse(Attachments.SELECTING_ATTACHMENT, 0), question.toRaw()));

        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (this.scrolling) {
            int scrollbarY = this.topPos + SCROLLBAR_Y;
            this.scrollOffs = Mth.clamp(((float) event.y() - (float) scrollbarY - 7.5F) / 97.0F, 0.0F, 1.0F);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!canScroll()) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        this.scrollOffs = Mth.clamp(this.scrollOffs - (float) (scrollY / (double) calculateRowCount()), 0.0F, 1.0F);
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (this.searchField.keyPressed(event)) {
            return true;
        }
        if (event.shortcutKey() == InputConstants.KEYCODE_RETURN || event.shortcutKey() == InputConstants.KEYCODE_NUMPADENTER) {
            this.search();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (this.searchField.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isInputCaptured() {
        return super.isInputCaptured() || this.searchField.capturesInput();
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int x = this.leftPos + SCROLLBAR_X;
        int y = this.topPos + SCROLLBAR_Y;
        return mouseX >= x && mouseX < x + 14 && mouseY >= y && mouseY < y + SCROLLBAR_HEIGHT;
    }

    private static boolean isInsideSlot(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    private int calculateRowCount() {
        return Mth.positiveCeilDiv(this.displayGroups.size(), COLUMNS) - ROWS;
    }

    private boolean canScroll() {
        return this.displayGroups.size() > COLUMNS * ROWS;
    }

    private int getRowIndexForScroll(float scrollOffs) {
        return Math.max((int) (scrollOffs * (float) calculateRowCount() + 0.5F), 0);
    }

}