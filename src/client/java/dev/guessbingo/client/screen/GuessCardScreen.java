package dev.guessbingo.client.screen;

import com.mojang.datafixers.util.Pair;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.client.ClientState;
import dev.guessbingo.client.DisplayGroup;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.lex.wrapped.QuestionState;
import dev.guessbingo.lex.wrapped.WrappedQuestion;
import dev.guessbingo.net.SelectData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class GuessCardScreen extends Screen {
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "slot");
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "unknown");
    private static final Identifier SELECTING = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "selecting");
    private static final Identifier PREDICT_SELECTING = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "predict_selecting");
    private static final Identifier COMPLETE = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "complete");

    private int predict = -1;
    private boolean allComplete = false;
    private final LocalPlayer player;
    private Button resetButton;

    public GuessCardScreen(Component title) {
        super(title);
        this.player = Minecraft.getInstance().player;
    }

    @Override
    protected void init() {
        super.init();
        this.resetButton = Button.builder(Component.literal("重置"), button ->
                        this.player.connection.sendCommand("bingo reset"))
                .pos(this.width / 2 - 40, this.height / 2 + 9 * getSide(ClientState.getState().size()) + 20)
                .size(80, 20)
                .build();
        this.resetButton.active = false;
        this.resetButton.visible = false;
        this.addWidget(this.resetButton);
    }

    /** 取消打开屏幕时对背后世界的背景模糊。 */
    @Override
    protected void extractBlurredBackground(@NonNull GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractMenuBackground(@NonNull GuiGraphicsExtractor graphics) {
        super.extractMenuBackground(graphics);
        List<Pair<WrappedQuestion, DisplayGroup>> questions = ClientState.getState();

        int size = questions.size();
        int side = getSide(size);

        int x0 = graphics.guiWidth() / 2 - 9 * side;
        int y0 = graphics.guiHeight() / 2 - 9 * side;
        for(int i = 0; i < side; i ++)
            for(int j = 0; j < side; j ++){
                int x= j * 18 + x0;
                int y= i * 18 + y0;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
            }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int select = player.getAttachedOrElse(Attachments.SELECTING_ATTACHMENT, 0);
        List<Pair<WrappedQuestion, DisplayGroup>> questions = ClientState.getState();

        int size = questions.size();
        int side = getSide(size);

        int x0 = graphics.guiWidth() / 2 - 9 * side;
        int y0 = graphics.guiHeight() / 2 - 9 * side;
        predict = -1;
        boolean flag = true;
        for(int i = 0; i < side; i ++)
            for(int j = 0; j < side; j ++){
                int index = i * side + j;
                int x= j * 18 + x0;
                int y= i * 18 + y0;

                if(index >= size) continue;

                Pair<WrappedQuestion, DisplayGroup> entry = questions.get(index);
                ItemStack itemStack = entry.getSecond().itemStack();
                int diff = entry.getFirst().difficulty();

                if(itemStack.is(Items.AIR))
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNKNOWN, x + 1, y + 1, 16, 16);
                else
                    graphics.item(itemStack, x + 1, y + 1);
                if(index == select){
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTING, x, y, 18, 18);
                }
                else if(x <= mouseX && mouseX <= x + 18 && y <= mouseY && mouseY <= y + 18){
                    predict = index;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PREDICT_SELECTING, x, y, 18, 18);
                }
                int color = switch (diff){
                    case 2 -> 0xff00ff00;
                    case 3 -> 0xffffff00;
                    case 4 -> 0xffff8800;
                    case 5 -> 0xffff0000;
                    default -> 0xffffffff;
                };
                graphics.text(Minecraft.getInstance().font, String.valueOf(diff), x + 11, y + 9, color);

                if(entry.getFirst().getCurrentState() == QuestionState.State.COMPLETED)
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, COMPLETE, x + 1, y + 1, 16, 16);
                else
                    flag = false;
            }
        allComplete = flag;
        this.resetButton.visible = allComplete;
        this.resetButton.active = allComplete;
        this.resetButton.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if(this.predict != -1)
            ClientPlayNetworking.send(new SelectData(this.predict));

        return super.mouseClicked(event, doubleClick);
    }

    private static int getSide(int s){
        for(int i = 0; i < 5 ; i ++){
            if(i * i >= s)
                return i;
        }
        return 5;
    }
}