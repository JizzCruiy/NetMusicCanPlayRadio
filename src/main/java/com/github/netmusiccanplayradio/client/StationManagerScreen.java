package com.github.netmusiccanplayradio.client;

import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetPickerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 玩家自定义电台管理界面（预设电台管理功能）。
 * <p>
 * 从 NetMusic"选择预设电台"页面的"管理电台"按钮进入，管理
 * {@code config/netmusiccanplayradio/stations.json} 中的玩家电台：
 * 添加（底部按钮）/ 修改（点台名）/ 删除（行尾"删"按钮）。
 * 每次保存后调用 NetMusic 的 public 方法 {@code loadBundledStations()} 刷新预设列表，
 * 返回预设页面即可看到合并后的列表（内置 + 玩家）。
 */
public class StationManagerScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final BigMegaphonePresetPickerScreen parent;
    private int leftPos;
    private int topPos;
    private int page;
    private List<CustomStations.Station> stations;

    public StationManagerScreen(BigMegaphonePresetPickerScreen parent) {
        super(Component.translatable("gui.netmusiccanplayradio.manage_stations.title"));
        this.parent = parent;
    }

    @Override
    public void init() {
        this.leftPos = (this.width - 240) / 2;
        this.topPos = (this.height - 200) / 2;
        this.stations = CustomStations.load();
        this.rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();

        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, this.stations.size());
        for (int i = start; i < end; i++) {
            int index = i - start;
            CustomStations.Station station = this.stations.get(i);
            int rowY = this.topPos + 20 + index * 22;
            // 点台名 = 修改
            this.addRenderableWidget(Button.builder(Component.literal(station.name()),
                            b -> openEditor(start + index, station.name(), station.url()))
                    .pos(this.leftPos, rowY)
                    .size(196, 20)
                    .build());
            // 行尾"删"按钮
            this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.delete"),
                            b -> deleteStation(start + index))
                    .pos(this.leftPos + 200, rowY)
                    .size(40, 20)
                    .build());
        }

        // 操作行：页码居中（y+134），"添加电台"按钮放同一行右侧，互不重叠
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.add_station"),
                        b -> openEditor(-1, "", ""))
                .pos(this.leftPos + 164, this.topPos + 134)
                .size(76, 20)
                .build());

        // 分页行：上一页 / 返回 / 下一页
        Button previous = Button.builder(Component.translatable("gui.netmusiccanplayradio.page.previous"), b -> doPrevious())
                .pos(this.leftPos, this.topPos + 158)
                .size(76, 20)
                .build();
        previous.active = this.page > 0;
        this.addRenderableWidget(previous);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusiccanplayradio.back"), b -> this.onClose())
                .pos(this.leftPos + 82, this.topPos + 158)
                .size(76, 20)
                .build());

        int maxPage = getMaxPage();
        Button next = Button.builder(Component.translatable("gui.netmusiccanplayradio.page.next"), b -> doNext(maxPage))
                .pos(this.leftPos + 164, this.topPos + 158)
                .size(76, 20)
                .build();
        next.active = this.page < maxPage;
        this.addRenderableWidget(next);
    }

    private void deleteStation(int configIndex) {
        CustomStations.remove(configIndex);
        this.stations = CustomStations.load();
        int maxPage = getMaxPage();
        if (this.page > maxPage) {
            this.page = maxPage;
        }
        this.rebuildButtons();
        refreshPresetList();
    }

    private void openEditor(int configIndex, String name, String url) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditStationScreen(this, configIndex, name, url));
        }
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

    /** 保存后刷新 NetMusic 预设列表（public 方法，重新走加载+合并） */
    public static void refreshPresetList() {
        try {
            com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetManager.loadBundledStations();
        } catch (Exception e) {
            // 忽略：下次打开预设页面/重载时会再刷新
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.topPos + 6, 0xFFFFFF);

        if (this.stations.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.netmusiccanplayradio.manage_stations.empty"),
                    this.width / 2, this.topPos + 70, 0xAAAAAA);
        } else {
            String pageText = "%d / %d".formatted(this.page + 1, this.getMaxPage() + 1);
            graphics.drawCenteredString(this.font, pageText, this.width / 2, this.topPos + 134, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
