package ru.minewatch.mod;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.SoundEvents;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

public class MineHudRenderer {

    private static final int ROW_H    = 13;
    private static final int HEADER_H = 14;
    private static final int PAD      = 5;
    private static final int R        = 4;
    private static final int BTN_W    = 11;
    private static final int BTN3_W   = 16;
    private static final int BTN4_W   = 13;
    private static final int BTN_H    = 9;

    private static final int C_BTN_FILT_ALL  = 0xFF888888;
    private static final int C_BTN_FILT_EL   = 0xFFCC88FF;
    private static final int C_BTN_FILT_LEG  = 0xFFFFAA00;
    private static final int C_BTN_FILT_EPIC = 0xFFAA66FF;
    private static final int C_HEADER   = 0xEE110D02;
    private static final int C_BG       = 0xDD0A0A0F;
    private static final int C_TITLE    = 0xFFFF9900;
    private static final int C_SUB      = 0xFF445566;
    private static final int C_DIV      = 0x20FF8800;
    private static final int C_GLOW1    = 0x06FF8800;
    private static final int C_GLOW2    = 0x14FF8800;
    private static final int C_GLOW3    = 0x24FF8800;
    private static final int C_ACT_BG   = 0x1AFF8800;
    private static final int C_ACT_BAR  = 0xCCFF8800;
    private static final int C_EMPTY    = 0xFF333344;
    private static final int C_ARROW    = 0xFF404055;
    private static final int C_BTN      = 0x33FFFFFF;
    private static final int C_BTN_PAUSE = 0xFF88CCFF;
    private static final int C_BTN_RESET = 0xFFFF6655;
    private static final int C_BTN_LIM   = 0xFFAABB66;
    private static final int C_ORD      = 0xFF999999;
    private static final int C_EPIC     = 0xFF9955EE;
    private static final int C_MYTH     = 0xFFEE33AA;
    private static final int C_LEG      = 0xFFFFAA00;
    private static final int C_ORD_DIM  = 0xFF555555;
    private static final int C_EPIC_DIM = 0xFF552288;
    private static final int C_MYTH_DIM = 0xFF881166;
    private static final int C_LEG_DIM  = 0xFF886600;
    private static final int C_TIME_OK  = 0xFF3EE87A;
    private static final int C_TIME_WRN = 0xFFFFCC44;
    private static final int C_TIME_CRT = 0xFFFF3344;
    private static final int C_MODE_A   = 0xFFEEEEEE;
    private static final int C_MODE     = 0xFF7788AA;
    private static final int C_OW       = 0xFF55AA44;
    private static final int C_AD       = 0xFFFF5533;
    private static final int C_END_W    = 0xFF9966EE;
    private static final int C_LEG_BG    = 0xDD1A0800;
    private static final int C_LEG_BORD  = 0xFFFFAA00;
    private static final int C_LEG_TEXT  = 0xFFFFDD88;
    private static final int C_REMOTE_BAR = 0xCC2255AA;

    private boolean mouseWasDown = false;
    private boolean endWasDown   = false;
    // Кнопки удаления строк в режиме deleteMode: [x, y] каждой строки
    private final java.util.List<int[]>  delBtns     = new java.util.ArrayList<>();
    private final java.util.List<String> delBtnModes = new java.util.ArrayList<>();
    private int btnPauseX, btnPauseY, btnResetX, btnResetY, btnLimX, btnLimY, btnFiltX, btnFiltY, btnGlobX, btnGlobY;
    private int lastW = 220;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc   = Minecraft.getInstance();
        MatrixStack ms = event.getMatrixStack();
        FontRenderer font = mc.font;

        if (MineData.hasLegendAlert()) renderLegendAlert(ms, font, mc);
        if (!MineData.isHudVisible()) return;

        int x = MineData.getHudX(), y = MineData.getHudY();
        String activeMode = MineData.getAnarchyMode();
        List<MineData.MineEntry> entries = MineData.getSortedEntries();
        int limit = MineData.getMaxEntries();
        if (entries.size() > limit) entries = entries.subList(0, limit);

        int W = computeWidth(font, entries);
        lastW = W;
        int totalH = HEADER_H + Math.max(1, entries.size()) * ROW_H + R;

        drawRR(ms, x-3, y-3, W+6, totalH+6, R+3, C_GLOW1);
        drawRR(ms, x-2, y-2, W+4, totalH+4, R+2, C_GLOW2);
        drawRR(ms, x-1, y-1, W+2, totalH+2, R+1, C_GLOW3);

        drawRRTop(ms, x, y, W, HEADER_H+R, R, C_HEADER);
        drawCircle(ms, x+PAD+2, y+5, 3, C_TITLE);
        font.draw(ms, "MineWatch", x+PAD+9, y+3, C_TITLE);

        btnResetX = x+W-BTN_W-PAD;       btnResetY = y+3;
        btnPauseX = btnResetX-BTN_W-2;   btnPauseY = y+3;
        btnLimX   = btnPauseX-BTN3_W-2;  btnLimY   = y+3;
        btnGlobX  = btnLimX-BTN_W-2;     btnGlobY  = y+3;
        btnFiltX  = btnGlobX-BTN4_W-2;   btnFiltY  = y+3;

        drawBtn(ms, font, btnResetX, btnResetY, BTN_W,  "x",
                MineData.isDeleteMode() ? 0xFFFF6600 : C_BTN_RESET);
        drawBtn(ms, font, btnPauseX, btnPauseY, BTN_W,  MineData.isPaused() ? ">" : "=", C_BTN_PAUSE);
        drawBtn(ms, font, btnLimX,   btnLimY,   BTN3_W, MineData.getMaxEntries()+"", C_BTN_LIM);
        drawBtn(ms, font, btnGlobX,  btnGlobY,  BTN_W,  "G", MineData.isGlobal() ? 0xFF44CCFF : 0xFF334455);
        drawBtnFilter(ms, font, btnFiltX, btnFiltY, BTN4_W, MineData.getFilterMode());

        String cnt = entries.size() + " серв.";
        font.draw(ms, cnt, btnFiltX-font.width(cnt)-3, y+3, C_SUB);

        drawRect(ms, x+R, y+HEADER_H-1, W-R*2, 1, C_DIV);
        drawRRBot(ms, x, y+HEADER_H, W, totalH-HEADER_H, R, C_BG);
        drawRect(ms, x, y+HEADER_H, W, R, C_BG);

        if (entries.isEmpty()) {
            font.draw(ms, "Ожидание...", x+PAD, y+HEADER_H+3, C_EMPTY); return;
        }
        if (MineData.isPaused()) {
            String s = "[ пауза ]";
            font.draw(ms, s, x+(W-font.width(s))/2, y+HEADER_H+3, 0x77AAAAAA); return;
        }

        delBtns.clear();
        delBtnModes.clear();
        int ry = y + HEADER_H;
        for (int i = 0; i < entries.size(); i++) {
            MineData.MineEntry e = entries.get(i);
            boolean isActive = e.mode.equals(activeMode);
            if (isActive) {
                drawRect(ms, x, ry, W, ROW_H, C_ACT_BG);
                drawRect(ms, x, ry, 2, ROW_H, C_ACT_BAR);
            } else if (e.remoteOnly) {
                drawRect(ms, x, ry, 2, ROW_H, C_REMOTE_BAR);
            }

            drawCircle(ms, x+PAD+2, ry+5, 3, bestMineColor(e));

            String label = e.mode.replace("Лайт","").replace("лайт","").trim();
            font.draw(ms, label, x+PAD+9, ry+3, isActive ? C_MODE_A : C_MODE);
            int cx = x + PAD + 9 + font.width(label);

            int bestRank = e.bestRank();
            for (Map.Entry<String, MineData.ShaftEntry> se : e.getSortedShafts().entrySet()) {
                MineData.ShaftEntry shaft = se.getValue();
                String shWorld = se.getKey();

                // Ад и Энд — одна шахта ("Шахта Ада и Энда"), рендерим только через Ад
                if ("Энд".equals(shWorld) && e.shafts.containsKey("Ад")) continue;

                // Для Ад — сливаем данные из Энд если своих нет
                String curType = shaft.currentType;
                String nxtType = shaft.nextType;
                int    secs    = shaft.hasTime() ? shaft.getRealSecs() : -1;
                if ("Ад".equals(shWorld)) {
                    MineData.ShaftEntry end = e.shafts.get("Энд");
                    if (end != null) {
                        if (curType.isEmpty()) curType = end.currentType;
                        if (nxtType.isEmpty()) nxtType = end.nextType;
                        int endS = end.hasTime() ? end.getRealSecs() : -1;
                        if (secs < 0) secs = endS;
                        else if (endS >= 0) secs = Math.min(secs, endS);
                    }
                }
                if (secs < 0 && curType.isEmpty() && nxtType.isEmpty()) continue;

                // Фильтр по рангу (используем слитые типы)
                if (!isActive) {
                    int rank = Math.max(typeRank(curType), typeRank(nxtType));
                    int fm   = MineData.getFilterMode();
                    if (fm == 1 && rank < 1) continue;
                    if (fm == 2 && rank < 3) continue;
                    if (fm == 3 && rank != 1) continue;
                }

                cx += 4;
                drawCircle(ms, cx+1, ry+5, 2, worldColor(shWorld));
                cx += 5;

                String curStr = shortType(curType);
                if (!curStr.isEmpty()) {
                    font.draw(ms, curStr, cx, ry+3, mineColor(curType, false));
                    cx += font.width(curStr);
                }
                String nxtStr = shortType(nxtType);
                if (!nxtStr.isEmpty()) {
                    font.draw(ms, ">", cx, ry+3, C_ARROW); cx += font.width(">");
                    font.draw(ms, nxtStr, cx, ry+3, mineColor(nxtType, true));
                    cx += font.width(nxtStr);
                }
                if (secs < 0) secs = e.getMinSecs();
                if (secs >= 0) {
                    String t = " " + MineData.fmt(secs);
                    int tc = secs > 60 ? C_TIME_OK : secs > 30 ? C_TIME_WRN : C_TIME_CRT;
                    font.draw(ms, t, cx, ry+3, tc); cx += font.width(t);
                }
            }

            if (MineData.isDeleteMode() && !isActive) {
                int dbx = x + W - BTN_W - PAD;
                int dby = ry + (ROW_H - BTN_H) / 2;
                drawBtn(ms, font, dbx, dby, BTN_W, "x", C_BTN_RESET);
                delBtns.add(new int[]{dbx, dby});
                delBtnModes.add(e.mode);
            }
            if (i < entries.size()-1)
                drawRect(ms, x+R, ry+ROW_H-1, W-R*2, 1, C_DIV);
            ry += ROW_H;
        }

        if (MineData.isDragging())
            font.draw(ms, "[ двигай ]", x+(W-font.width("[ двигай ]"))/2, y+3, 0x88FFFFFF);
    }

    private int computeWidth(FontRenderer font, List<MineData.MineEntry> entries) {
        int min = PAD + font.width("MineWatch") + 4
                + font.width("15 серв.") + BTN4_W + BTN3_W + BTN_W*3 + PAD + 30;
        int max = min;
        String activeMode2 = MineData.getAnarchyMode();
        for (MineData.MineEntry e : entries) {
            boolean isActiveEntry = e.mode.equals(activeMode2);
            int w = PAD + 9 + font.width(e.mode.replace("Лайт","").replace("лайт","").trim());
            int bestRank = e.bestRank();
            for (Map.Entry<String, MineData.ShaftEntry> se2 : e.getSortedShafts().entrySet()) {
                String shWorld2 = se2.getKey();
                if ("Энд".equals(shWorld2) && e.shafts.containsKey("Ад")) continue;
                MineData.ShaftEntry shaft = se2.getValue();
                String curType2 = shaft.currentType;
                String nxtType2 = shaft.nextType;
                if ("Ад".equals(shWorld2)) {
                    MineData.ShaftEntry end2 = e.shafts.get("Энд");
                    if (end2 != null) {
                        if (curType2.isEmpty()) curType2 = end2.currentType;
                        if (nxtType2.isEmpty()) nxtType2 = end2.nextType;
                    }
                }
                if (!shaft.hasTime() && curType2.isEmpty() && nxtType2.isEmpty()) continue;
                if (!isActiveEntry) {
                    int rank2 = Math.max(typeRank(curType2), typeRank(nxtType2));
                    int fm2   = MineData.getFilterMode();
                    if (fm2 == 1 && rank2 < 1) continue;
                    if (fm2 == 2 && rank2 < 3) continue;
                    if (fm2 == 3 && rank2 != 1) continue;
                }
                w += 4 + 5;
                w += font.width(shortType(curType2));
                String nxt2 = shortType(nxtType2);
                if (!nxt2.isEmpty()) w += font.width(">") + font.width(nxt2);
                w += font.width(" 15:00");
            }
            w += PAD;
            max = Math.max(max, w);
        }
        return Math.max(180, max);
    }

    private void drawBtnFilter(MatrixStack ms, FontRenderer font, int bx, int by, int bw, int mode) {
        drawRR(ms, bx-1, by-1, bw+2, BTN_H+2, 2, 0x22FFFFFF);
        if (mode == 1) {
            drawRect(ms, bx,      by, bw/2,    BTN_H, 0x669955EE);
            drawRect(ms, bx+bw/2, by, bw-bw/2, BTN_H, 0x66FFAA00);
        } else {
            drawRR(ms, bx, by, bw, BTN_H, 2, C_BTN);
        }
        String label; int lc;
        switch (mode) {
            case 1:  label="ЭЛ"; lc=0xFFEECCFF; break;
            case 2:  label="Л";  lc=C_LEG;      break;
            case 3:  label="Э";  lc=C_EPIC;     break;
            default: label="*";  lc=0xFF667788; break;
        }
        font.draw(ms, label, bx+(bw-font.width(label))/2, by+1, lc);
    }

    private void renderLegendAlert(MatrixStack ms, FontRenderer font, Minecraft mc) {
        String info = MineData.getLegendAlertText();
        if (info == null) return;
        String line1 = "Легендарная шахта";
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int bw = Math.max(font.width(line1), font.width(info)) + 24;
        int bh = 28;
        int bx = (sw - bw) / 2;
        int by = sh - 22 - bh - 4;
        drawRR(ms, bx-1, by-1, bw+2, bh+2, 4, C_LEG_BORD);
        drawRR(ms, bx,   by,   bw,   bh,   4, C_LEG_BG);
        drawRect(ms, bx+3, by+1, bw-6, 1, C_LEG_BORD);
        font.draw(ms, line1, bx+(bw-font.width(line1))/2, by+4,  C_LEG_BORD);
        font.draw(ms, info,  bx+(bw-font.width(info))/2,  by+15, C_LEG_TEXT);
    }

    private void drawBtn(MatrixStack ms, FontRenderer font, int bx, int by, int bw, String label, int lc) {
        drawRR(ms, bx-1, by-1, bw+2, BTN_H+2, 2, 0x22FFFFFF);
        drawRR(ms, bx,   by,   bw,   BTN_H,   2, C_BTN);
        font.draw(ms, label, bx+(bw-font.width(label))/2, by+1, lc);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long win = mc.getWindow().getWindow();
        boolean endDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_END) == GLFW.GLFW_PRESS;
        if (endDown && !endWasDown) MineData.toggleHudVisible();
        endWasDown = endDown;

        boolean ralt      = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean mouseDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double[] mxA = new double[1], myA = new double[1];
        GLFW.glfwGetCursorPos(win, mxA, myA);
        double scale = mc.getWindow().getGuiScale();
        int mx = (int)(mxA[0]/scale), my = (int)(myA[0]/scale);
        if (!mouseDown && mouseWasDown && !ralt) {
            if (inBtn(mx,my,btnPauseX,btnPauseY,BTN_W))  MineData.togglePause();
            if (inBtn(mx,my,btnResetX,btnResetY,BTN_W))  MineData.toggleDeleteMode();
            if (inBtn(mx,my,btnLimX,  btnLimY,  BTN3_W)) MineData.cycleMaxEntries();
            if (inBtn(mx,my,btnGlobX, btnGlobY, BTN_W))  MineData.toggleGlobal();
            if (inBtn(mx,my,btnFiltX, btnFiltY, BTN4_W)) MineData.cycleFilter();
            for (int di = 0; di < delBtns.size(); di++) {
                int[] db = delBtns.get(di);
                if (inBtn(mx, my, db[0], db[1], BTN_W)) {
                    MineData.removeEntry(delBtnModes.get(di));
                    MineData.exitDeleteMode();
                    break;
                }
            }
        }
        List<MineData.MineEntry> entries = MineData.getSortedEntries();
        int totalH = HEADER_H + Math.max(1, entries.size()) * ROW_H + R;
        int x = MineData.getHudX(), y = MineData.getHudY();
        if (ralt && mouseDown && !mouseWasDown) {
            if (mx>=x && mx<=x+lastW && my>=y && my<=y+totalH) MineData.startDrag(mx, my);
        } else if (ralt && mouseDown && MineData.isDragging()) {
            MineData.updateDrag(mx, my);
        } else if (!ralt || !mouseDown) { MineData.stopDrag(); }
        mouseWasDown = mouseDown;
        if (!MineData.hasData()) return;
        int secs = MineData.getRealTimeSeconds();
        for (int i = 0; i < MineData.ALERT_THRESHOLDS.length; i++) {
            if (secs <= MineData.ALERT_THRESHOLDS[i] && secs > MineData.ALERT_THRESHOLDS[i]-2
                    && MineData.shouldAlert(i)) playAlert(mc, i);
        }
        if (MineData.hasLegendAlert() && mc.player != null && System.currentTimeMillis()%2000<60)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL, 1.0f, 1.8f);
    }

    private static boolean shaftMatchesFilter(MineData.ShaftEntry shaft, int filterMode) {
        if (filterMode == 0) return true;
        int rank = Math.max(typeRank(shaft.currentType), typeRank(shaft.nextType));
        if (filterMode == 1) return rank >= 1;
        if (filterMode == 2) return rank >= 3;
        if (filterMode == 3) return rank == 1;
        return true;
    }

    private static int mineColor(String type, boolean dim) {
        if (type == null || type.isEmpty()) return dim ? C_ORD_DIM : C_ORD;
        String l = type.toLowerCase();
        if (l.contains("легенд")) return dim ? C_LEG_DIM  : C_LEG;
        if (l.contains("мифич"))  return dim ? C_MYTH_DIM : C_MYTH;
        if (l.contains("эпич"))   return dim ? C_EPIC_DIM : C_EPIC;
        return dim ? C_ORD_DIM : C_ORD;
    }
    private static int worldColor(String w) {
        if (w == null) return C_OW;
        String l = w.toLowerCase();
        if (l.contains("ад"))  return C_AD;
        if (l.contains("энд")) return C_END_W;
        return C_OW;
    }
    private static String shortType(String m) {
        if (m == null || m.isEmpty()) return "";
        int dot = m.indexOf("·");
        String base = dot >= 0 ? m.substring(dot+1).trim() : m;
        String l = base.toLowerCase();
        if (l.contains("легенд")) return "Лег.";
        if (l.contains("мифич"))  return "Миф.";
        if (l.contains("эпич"))   return "Эп.";
        if (l.contains("обычн"))  return "Об.";
        return "";
    }
    private static int bestMineColor(MineData.MineEntry e) {
        int best = 0;
        for (MineData.ShaftEntry s : e.shafts.values()) {
            best = Math.max(best, typeRank(s.currentType));
            best = Math.max(best, typeRank(s.nextType));
        }
        switch (best) {
            case 3: return C_LEG;
            case 2: return C_MYTH;
            case 1: return C_EPIC;
            default: return C_ORD;
        }
    }
    private static int typeRank(String t) {
        if (t == null) return 0;
        String l = t.toLowerCase();
        if (l.contains("легенд")) return 3;
        if (l.contains("мифич"))  return 2;
        if (l.contains("эпич"))   return 1;
        return 0;
    }
    private static boolean inBtn(int mx,int my,int bx,int by,int bw) {
        return mx>=bx && mx<=bx+bw && my>=by && my<=by+BTN_H;
    }
    private static void drawCircle(MatrixStack ms,int cx,int cy,int r,int color) {
        for(int dy=-r;dy<=r;dy++) for(int dx=-r;dx<=r;dx++)
            if(dx*dx+dy*dy<=r*r) drawRect(ms,cx+dx,cy+dy,1,1,color);
    }
    private static void drawRR(MatrixStack ms,int x,int y,int w,int h,int r,int color) {
        drawRect(ms,x+r,y,w-r*2,h,color); drawRect(ms,x,y+r,r,h-r*2,color);
        drawRect(ms,x+w-r,y+r,r,h-r*2,color);
        corner(ms,x,y,r,color,0); corner(ms,x+w-r,y,r,color,1);
        corner(ms,x,y+h-r,r,color,2); corner(ms,x+w-r,y+h-r,r,color,3);
    }
    private static void drawRRTop(MatrixStack ms,int x,int y,int w,int h,int r,int color) {
        drawRect(ms,x+r,y,w-r*2,h,color); drawRect(ms,x,y+r,r,h-r,color);
        drawRect(ms,x+w-r,y+r,r,h-r,color);
        corner(ms,x,y,r,color,0); corner(ms,x+w-r,y,r,color,1);
    }
    private static void drawRRBot(MatrixStack ms,int x,int y,int w,int h,int r,int color) {
        drawRect(ms,x+r,y,w-r*2,h,color); drawRect(ms,x,y,r,h-r,color);
        drawRect(ms,x+w-r,y,r,h-r,color);
        corner(ms,x,y+h-r,r,color,2); corner(ms,x+w-r,y+h-r,r,color,3);
    }
    private static void corner(MatrixStack ms,int cx,int cy,int r,int color,int q) {
        for(int dy=0;dy<r;dy++) for(int dx=0;dx<r;dx++) {
            if(Math.sqrt((r-1-dx)*(double)(r-1-dx)+(r-1-dy)*(double)(r-1-dy))<r) {
                int px=(q==1||q==3)?cx+(r-1-dx):cx+dx;
                int py=(q==2||q==3)?cy+(r-1-dy):cy+dy;
                drawRect(ms,px,py,1,1,color);
            }
        }
    }
    private static void drawRect(MatrixStack ms,int x,int y,int w,int h,int color) {
        net.minecraft.client.gui.AbstractGui.fill(ms,x,y,x+w,y+h,color);
    }
    private void playAlert(Minecraft mc,int i) {
        if(mc.player==null) return;
        switch(i) {
            case 0: mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING,0.5f,1.0f); break;
            case 1: mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING,0.8f,1.5f); break;
            case 2: mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING,1.0f,2.0f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING,1.0f,2.0f); break;
        }
    }
}
