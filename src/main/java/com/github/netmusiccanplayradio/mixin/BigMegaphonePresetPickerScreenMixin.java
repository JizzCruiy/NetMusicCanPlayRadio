package com.github.netmusiccanplayradio.mixin;

import com.github.netmusiccanplayradio.client.StationManagerScreen;
import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetPickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 NetMusic 的"选择预设电台"页面加"管理电台"入口按钮。
 * <p>
 * 注入 {@code rebuildPresetButtons}（翻页时会 clearWidgets 后重建，本按钮随每次重建追加，
 * 因此翻页不会丢失）；点击打开 {@link StationManagerScreen} 管理玩家自定义电台。
 * <p>
 * 实现说明：{@code addRenderableWidget} 是 {@link Screen} 的 protected final 方法，
 * Mixin 的 @Shadow 无法指向父类 final 方法（会导致注入失败崩溃），因此本 Mixin 类
 * 直接 {@code extends Screen}，使注入代码能通过 this 调用该方法（Mixin 类本身不实例化，
 * 仅编译期继承，无运行时开销）。
 * <p>
 * 注意：{@code remap = false} —— 注入的是 mod 类（不参与 MC 混淆映射）。
 */
@Mixin(value = BigMegaphonePresetPickerScreen.class, remap = false)
public abstract class BigMegaphonePresetPickerScreenMixin extends Screen {
    @Shadow
    private int leftPos;

    @Shadow
    private int topPos;

    /** 仅为编译期提供 this.addRenderableWidget 的访问（Mixin 类不实例化） */
    protected BigMegaphonePresetPickerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "rebuildPresetButtons", at = @At("RETURN"))
    private void netmusiccanplayradio$addManageButton(CallbackInfo ci) {
        BigMegaphonePresetPickerScreen self = (BigMegaphonePresetPickerScreen) (Object) this;
        // 页码文字居中，本按钮放右侧一行（y+134，列表下方、作者分页按钮上方）
        Button manage = Button.builder(
                        Component.translatable("gui.netmusiccanplayradio.manage_stations"),
                        b -> Minecraft.getInstance().setScreen(new StationManagerScreen(self)))
                .pos(this.leftPos + 164, this.topPos + 134)
                .size(76, 20)
                .build();
        this.addRenderableWidget(manage);
    }
}
