package com.luma.home;

/** 图标库:key -> 资源 id。参考 Aerial 的做法,按"接的是什么设备"来分。 */
final class IconLib {

    static final String APPLETV = "appletv";
    static final String PS = "ps";
    static final String SWITCH = "switch";
    static final String XBOX = "xbox";
    static final String PC = "pc";
    static final String BLURAY = "bluray";
    static final String BOX = "box";
    static final String PROJECTOR = "projector";
    static final String SPEAKER = "speaker";
    static final String HDMI = "hdmi";
    static final String MEDIA = "media";
    static final String TV = "tv";
    static final String SOUNDBAR = "soundbar";
    static final String RECEIVER = "receiver";
    static final String NAS = "nas";
    static final String CAMERA = "camera";

    /** 图标选择器的展示顺序 */
    static final String[] ALL = {
            TV, BOX, HDMI, MEDIA, SOUNDBAR, RECEIVER, NAS,
            PS, SWITCH, XBOX, APPLETV, PC, BLURAY, PROJECTOR, SPEAKER, CAMERA
    };

    private IconLib() {
    }

    static int res(String key) {
        if (key == null) {
            return R.drawable.ic_dev_hdmi;
        }
        switch (key) {
            case APPLETV: return R.drawable.ic_dev_appletv;
            case PS: return R.drawable.ic_dev_ps;
            case SWITCH: return R.drawable.ic_dev_switch;
            case XBOX: return R.drawable.ic_dev_xbox;
            case PC: return R.drawable.ic_dev_pc;
            case BLURAY: return R.drawable.ic_dev_bluray;
            case BOX: return R.drawable.ic_dev_box;
            case PROJECTOR: return R.drawable.ic_dev_projector;
            case SPEAKER: return R.drawable.ic_dev_speaker;
            case MEDIA: return R.drawable.ic_dev_media;
            case TV: return R.drawable.ic_dev_tv;
            case SOUNDBAR: return R.drawable.ic_dev_soundbar;
            case RECEIVER: return R.drawable.ic_dev_receiver;
            case NAS: return R.drawable.ic_dev_nas;
            case CAMERA: return R.drawable.ic_dev_camera;
            case HDMI:
            default: return R.drawable.ic_dev_hdmi;
        }
    }


    static String label(android.content.Context c, String key) {        if (key == null) {
            return c.getString(R.string.icon_hdmi);
        }
        switch (key) {
            case APPLETV: return c.getString(R.string.icon_appletv);
            case PS: return c.getString(R.string.icon_ps);
            case SWITCH: return c.getString(R.string.icon_switch);
            case XBOX: return c.getString(R.string.icon_xbox);
            case PC: return c.getString(R.string.icon_pc);
            case BLURAY: return c.getString(R.string.icon_bluray);
            case BOX: return c.getString(R.string.icon_box);
            case PROJECTOR: return c.getString(R.string.icon_projector);
            case SPEAKER: return c.getString(R.string.icon_speaker);
            case MEDIA: return c.getString(R.string.icon_media);
            case TV: return c.getString(R.string.icon_tv);
            case SOUNDBAR: return c.getString(R.string.icon_soundbar);
            case RECEIVER: return c.getString(R.string.icon_receiver);
            case NAS: return c.getString(R.string.icon_nas);
            case CAMERA: return c.getString(R.string.icon_camera);
            case HDMI:
            default: return c.getString(R.string.icon_hdmi);
        }
    }
}
