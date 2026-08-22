package com.github.netmusiccanplayradio.client;

import com.github.tartaricacid.netmusic.util.BigMegaphoneUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 添加/修改电台输入界面。
 * <p>
 * 两个输入框（名称/URL）+ 保存/取消；保存时校验（非空 + NetMusic 的 URL 校验，
 * 本 mod 的 Mixin 已放行非 .m3u8 直链），写回 {@code config/netmusiccanplayradio/stations.json}
 * 并刷新 NetMusic 预设列表，然后返回管理界面。
 */
public class EditStationScreen extends Screen {
    private static final int WIDTH = 240;

    private final StationManagerScreen parent;
    private final int configIndex;   // -1 = 添加，>=0 = 修改

    private int leftPos;
    private int topPos;
    private EditBox nameBox;
    private EditBox urlBox;
    private Component tips = Component.empty();

    public EditStationScreen(StationManagerScreen parent, int configIndex, String name, String url) {
        super(Component.translatable(configIndex < 0
                ? "gui.netmusiccanplayradio.add_station.title"
                : "gui.netmusiccanplayradio.edit_station.title"));
        this.parent = parent;
        this.configIndex = configIndex;
        this.nameValue = name;
        this.urlValue = url;
    }

    private final String nameValue;
    private final String urlValue;

    @Override
    public void init() {
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - 140) / 2;

        this.nameBox = new EditBox(this.font, this.leftPos, this.topPos + 30, WIDTH, 18,
                Component.literal("Station Name Box"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.nameValue);
        this.addRenderableWidget(this.nameBox);

        this.urlBox = new EditBox(this.font, this.leftPos, this.topPos + 66, WIDTH, 18,
                Component.literal("Station URL Box"));
        this.urlBox.setMaxLength(1024);
        this.urlBox.setValue(this.urlValue);
        this.addRenderableWidget(this.urlBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.save"),
                        b -> this.save())
                .pos(this.leftPos, this.topPos + 100)
                .size(76, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.cancel"),
                        b -> this.onClose())
                .pos(this.leftPos + 164, this.topPos + 100)
                .size(76, 20)
                .build());
    }

    private void save() {
        String name = this.nameBox.getValue().trim();
        String url = this.urlBox.getValue().trim();
        if (name.isBlank()) {
            this.tips = Component.translatable("gui.netmusiccanplayradio.name.empty");
            return;
        }
        if (url.isBlank() || !BigMegaphoneUtil.isValidStreamUrl(url)) {
            this.tips = Component.translatable("gui.netmusiccanplayradio.url.invalid");
            return;
        }
        if (this.configIndex < 0) {
            CustomStations.add(name, url);
        } else {
            CustomStations.update(this.configIndex, name, url);
        }
        StationManagerScreen.refreshPresetList();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.topPos + 8, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.netmusiccanplayradio.name"),
                this.leftPos + 2, this.topPos + 18, 0xAAAAAA, false);
        graphics.drawString(this.font, Component.translatable("gui.netmusiccanplayradio.url"),
                this.leftPos + 2, this.topPos + 54, 0xAAAAAA, false);
        if (!this.tips.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.tips, this.width / 2, this.topPos + 90, 0xCF0000);
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
