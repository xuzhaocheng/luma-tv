package com.luma.home;

/** 一路信号源:系统给的是数字 id,名字和图标可以由用户改。 */
public class SourceItem {

    public final int id;
    public String name;
    public String iconKey;
    public boolean pinned;
    /** 设为「首页常用」的时间,用于固定位满时淘汰最早的那个 */
    public long pinnedAt;

    public SourceItem(int id, String name, String iconKey) {
        this.id = id;
        this.name = name;
        this.iconKey = iconKey;
    }

    @Override
    public String toString() {
        return id + ":" + name;
    }
}
