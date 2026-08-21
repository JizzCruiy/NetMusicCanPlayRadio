package com.github.netmusiccanplayradio.client;

import com.github.tartaricacid.netmusic.client.gui.BigMegaphoneScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 玩家自定义电台选择界面（docs/09 提案 2b）。
 * <p>
 * 风格与 NetMusic 的 {@code BigMegaphonePresetPickerScreen} 一致（每页 5 个 + 分页），
 * 数据来自 {@link CustomStationManager}；选中后调用大喇叭界面的 public 方法
 * {@code applyPresetStation(name, url)} 填入 URL/名称，不侵入 NetMusic 内部。
 */
public class MyStationsScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final BigMegaphoneScreen parent;
    private int leftPos;
    private int topPos;
    private int page;
    private List<CustomStationManager.Station> stations;

    public MyStationsScreen(BigMegaphoneScreen parent) {
        super(Component.translatable("gui.netmusiccanplayradio.my_stations.title"));
        this.parent = parent;
    }

    @Override
    public void init() {
        this.leftPos = (this.width - 240) / 2;
        this.topPos = (this.height - 170) / 2;
        // 每次打开都重新读取，玩家在游戏外编辑 config 后重开界面即生效
        this.stations = CustomStationManager.loadStations();
        this.rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();

        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, this.stations.size());
        for (int i = start; i < end; i++) {
            int index = i - start;
            CustomStationManager.Station station = this.stations.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(station.name()),
                            b -> this.selectStation(station))
                    .pos(this.leftPos, this.topPos + 20 + index * 22)
                    .size(240, 20)
                    .build());
        }

        Button previous = Button.builder(Component.translatable("gui.netmusiccanplayradio.page.previous"), b -> doPrevious())
                .pos(this.leftPos, this.topPos + 156)
                .size(76, 20)
                .build();
        previous.active = this.page > 0;
        this.addRenderableWidget(previous);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.back"), b -> this.onClose())
                .pos(this.leftPos + 82, this.topPos + 156)
                .size(76, 20)
                .build());

        int maxPage = getMaxPage();
        Button next = Button.builder(Component.translatable("gui.netmusiccanplayradio.page.next"), b -> doNext(maxPage))
                .pos(this.leftPos + 164, this.topPos + 156)
                .size(76, 20)
                .build();
        next.active = this.page < maxPage;
        this.addRenderableWidget(next);
    }

    private void doNext(int maxPage) {
        if (this.page < maxPage) {
            this.page++;
            this.rebuildButtons();
        }
    }

    private void doPrevious() {
        if (this.page > 0) {
            this.page--;
            this.rebuildButtons();
        }
    }

    private int getMaxPage() {
        int size = this.stations.size();
        return size == 0 ? 0 : (size - 1) / PAGE_SIZE;
    }

    private void selectStation(CustomStationManager.Station station) {
        this.parent.applyPresetStation(station.name(), station.url());
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
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.topPos + 6, 0xFFFFFF);

        if (this.stations.isEmpty()) {
            // 无自定义电台：提示文件位置
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.netmusiccanplayradio.my_stations.empty"),
                    this.width / 2, this.topPos + 60, 0xAAAAAA);
            graphics.drawCenteredString(this.font,
                    Component.literal("config/netmusiccanplayradio/stations.json"),
                    this.width / 2, this.topPos + 78, 0x888888);
        } else {
            String pageText = "%d / %d".formatted(this.page + 1, this.getMaxPage() + 1);
            graphics.drawCenteredString(this.font, pageText, this.width / 2, this.topPos + 138, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
