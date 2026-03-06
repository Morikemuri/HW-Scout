package net.kyrptonaught.hotkeybind.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.kyrptonaught.hotkeybind.HotKeybindMod;
import net.kyrptonaught.hotkeybind.config.ConfigOptions;
import net.kyrptonaught.hotkeybind.config.MacroConfig;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fully custom-rendered GUI - no phantom Forge Button boxes.
 * All clickable zones are tracked manually via ClickZone list.
 */
public class MacrosScreen extends Screen {

    private static int sExpanded     = -1;
    private static int sScrollOffset = 0;

    private static final int RH  = 20;  // row height
    private static final int RG  = 3;   // row gap
    private static final int PAD = 10;  // left/right padding

    private static final List<String> TYPES = Arrays.asList(
        "SingleUse","Repeating","Delayed","DisplayOnly","ToggledRepeating");

    // A clickable zone registered each frame
    private static class Zone {
        int x,y,w,h; Runnable action;
        Zone(int x,int y,int w,int h,Runnable a){this.x=x;this.y=y;this.w=w;this.h=h;this.action=a;}
        boolean hit(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    private final List<Zone> zones = new ArrayList<>();

    private final Screen parent;
    private int listening = 0; // 0=none 1=key 2=mod

    private TextFieldWidget cmdField;
    private TextFieldWidget delayField;
    // references for updating button labels after key-listen
    private String[] keyLabel    = {""};
    private String[] modLabel    = {""};
    private boolean  enabledVal  = true;

    public MacrosScreen(Screen parent) {
        super(new StringTextComponent("Macros"));
        this.parent = parent;
    }

    private void resetPersist() { sExpanded=-1; sScrollOffset=0; }

    // ---------------------------------------------------------------
    // init: only TextFields go through addWidget (they need focus mgmt)
    // ---------------------------------------------------------------
    @Override
    protected void init() {
        listening=0; cmdField=null; delayField=null;
        if (HotKeybindMod.config==null) return;
        enabledVal = HotKeybindMod.config.enabled;

        if (sExpanded>=0 && sExpanded<HotKeybindMod.config.macros.size()) {
            ConfigOptions.ConfigMacro m = HotKeybindMod.config.macros.get(sExpanded);
            // Command text field
            int valX = width/2; int valW = width-PAD-valX;
            int fy = rowY(2);  // row index 2 = first sub-row
            cmdField = new TextFieldWidget(font, valX, fy, valW, RH-2, new StringTextComponent(""));
            cmdField.setValue(m.command); cmdField.setMaxLength(256);
            cmdField.setFocus(false); addWidget(cmdField);
            // Delay text field
            int dy = rowY(5);
            delayField = new TextFieldWidget(font, valX, dy, valW, RH-2, new StringTextComponent(""));
            delayField.setValue(String.valueOf(m.delay));
            delayField.setFilter(s -> s.matches("\\d*"));
            delayField.setFocus(false); addWidget(delayField);
        }
    }

    // Y position of a row by absolute index (0=Enabled,1=macro header,2..7=sub rows,etc.)
    // Actually we compute dynamically in render/click - see buildRows()
    private int rowY(int idx) { return 30 - sScrollOffset + idx*(RH+RG); }

    // ---------------------------------------------------------------
    // Helpers for drawing
    // ---------------------------------------------------------------
    private static final int COL_ROW_BG  = 0xFF3C3F41;
    private static final int COL_BTN     = 0xFF5A5D5E;
    private static final int COL_BTN_HOV = 0xFF7A7D7E;
    private static final int COL_RST     = 0xFF5A3A3A;
    private static final int COL_RST_HOV = 0xFF8A5A5A;
    private static final int COL_GREEN   = 0xFF3A6E3A;
    private static final int COL_HEADER  = 0xFF4A4D4F;
    private static final int COL_HDR_HOV = 0xFF6A6D6F;

    private void fillRow(MatrixStack ms, int y, int col) {
        AbstractGui.fill(ms, 0, y, width, y+RH, col);
    }
    private void fillBtn(MatrixStack ms, int x, int y, int w, int h, int col, double mx, double my) {
        boolean hov = mx>=x&&mx<x+w&&my>=y&&my<y+h;
        AbstractGui.fill(ms, x, y, x+w, y+h, hov ? brighten(col) : col);
    }
    private static int brighten(int c) {
        int r=Math.min(255,((c>>16)&0xFF)+30);
        int g=Math.min(255,((c>>8)&0xFF)+30);
        int b=Math.min(255,(c&0xFF)+30);
        return (c&0xFF000000)|(r<<16)|(g<<8)|b;
    }

    // ---------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------
    @Override
    public void render(MatrixStack ms, int mx, int my, float pt) {
        renderBackground(ms);
        zones.clear();

        drawCenteredString(ms, font, "Macros", width/2, 8, 0xFFFFFF);

        if (HotKeybindMod.config==null) return;

        int RW  = 60;                    // reset button width
        int rstX = width-PAD-RW;         // reset button x
        int valX = rstX-PAD-200;         // value widget x (200px wide)
        if (valX < width/3) valX = width/3;
        int valW = rstX-PAD-valX;        // value widget width

        int y = 26 - sScrollOffset;

        // ---- Включено row ----
        fillRow(ms, y, COL_ROW_BG);
        font.draw(ms, "\u0412\u043a\u043b\u044e\u0447\u0435\u043d\u043e", PAD, y+(RH-8)/2, 0xFFFFFF);
        // Value button: Да / Нет
        String enLbl = enabledVal ? "\u00a7a\u0414\u0430" : "\u00a7c\u041d\u0435\u0442";
        fillBtn(ms, valX, y, valW, RH, enabledVal ? COL_GREEN : COL_BTN, mx, my);
        drawCenteredString(ms, font, enLbl, valX+valW/2, y+(RH-8)/2, 0xFFFFFF);
        { final int fy=y; zones.add(new Zone(valX,fy,valW,RH,()->{
            enabledVal=!enabledVal; HotKeybindMod.config.enabled=enabledVal; })); }
        // Reset button
        fillBtn(ms, rstX, y, RW, RH, COL_RST, mx, my);
        drawCenteredString(ms, font, "\u0421\u0431\u0440\u043e\u0441", rstX+RW/2, y+(RH-8)/2, 0xFFFFFF);
        { final int fy=y; zones.add(new Zone(rstX,fy,RW,RH,()->{
            enabledVal=true; HotKeybindMod.config.enabled=true; })); }
        y += RH+RG;

        // ---- Macro rows ----
        for (int i=0; i<HotKeybindMod.config.macros.size(); i++) {
            final int idx=i;
            ConfigOptions.ConfigMacro m = HotKeybindMod.config.macros.get(i);
            boolean exp = sExpanded==i;
            // Header
            fillRow(ms, y, exp ? COL_HDR_HOV : COL_HEADER);
            // Hover effect
            if (mx>=0&&mx<width&&my>=y&&my<y+RH)
                AbstractGui.fill(ms,0,y,width,y+RH, 0x22FFFFFF);
            font.draw(ms, (exp?"- ":"+ ")+m.command, PAD, y+(RH-8)/2, 0xFFFFFF);
            { final int fy=y;
              zones.add(new Zone(0,fy,width,RH,()->{
                  saveExpanded(); sExpanded=(sExpanded==idx)?-1:idx;
                  minecraft.setScreen(new MacrosScreen(parent)); })); }
            y+=RH+RG;

            if (exp) {
                // Команда
                y=drawSubRow(ms,mx,my,zones,y,"\u041a\u043e\u043c\u0430\u043d\u0434\u0430",valX,valW,rstX,RW,null,null);
                if (cmdField!=null) { cmdField.x=valX; cmdField.y=y-RH-RG; cmdField.setWidth(valW); }

                // Клавиша
                String kl = (listening==1)? "> \u041d\u0430\u0436\u043c\u0438\u0442\u0435 <" : m.keyName;
                y=drawSubRow(ms,mx,my,zones,y,"\u041a\u043b\u0430\u0432\u0438\u0448\u0430",valX,valW,rstX,RW,kl,()->{
                    if(listening!=1) listening=1;
                });
                final int ky=y-RH-RG;
                zones.add(new Zone(rstX,ky,RW,RH,()->{
                    m.keyName="key.keyboard.unknown"; if(listening==1) listening=0; }));

                // Клавиша модификатор
                String ml2 = (listening==2)? "> \u041d\u0430\u0436\u043c\u0438\u0442\u0435 <" : m.keyModName;
                y=drawSubRow(ms,mx,my,zones,y,"\u041a\u043b\u0430\u0432\u0438\u0448\u0430 \u043c\u043e\u0434\u0438\u0444\u0438\u043a\u0430\u0442\u043e\u0440",valX,valW,rstX,RW,ml2,()->{
                    if(listening!=2) listening=2;
                });
                final int my2=y-RH-RG;
                zones.add(new Zone(rstX,my2,RW,RH,()->{
                    m.keyModName="key.keyboard.unknown"; if(listening==2) listening=0; }));

                // Вид макроса
                y=drawSubRow(ms,mx,my,zones,y,"\u0412\u0438\u0434 \u043c\u0430\u043a\u0440\u043e\u0441\u0430",valX,valW,rstX,RW,m.type,()->{
                    int c=TYPES.indexOf(m.type); m.type=TYPES.get(((c<0?0:c)+1)%TYPES.size()); });
                final int ty=y-RH-RG;
                zones.add(new Zone(rstX,ty,RW,RH,()->{ m.type="SingleUse"; }));

                // Задержка
                y=drawSubRow(ms,mx,my,zones,y,"\u0417\u0430\u0434\u0435\u0440\u0436\u043a\u0430",valX,valW,rstX,RW,null,null);
                if (delayField!=null) { delayField.x=valX; delayField.y=y-RH-RG; delayField.setWidth(valW); }
                final int dy2=y-RH-RG;
                zones.add(new Zone(rstX,dy2,RW,RH,()->{ if(delayField!=null) delayField.setValue("0"); m.delay=0; }));

                // Удалить
                fillRow(ms, y, COL_ROW_BG);
                font.draw(ms, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c", PAD+20, y+(RH-8)/2, 0xFFFFFF);
                fillBtn(ms, valX, y, valW+PAD+RW, RH, 0xFF6E2020, mx, my);
                drawCenteredString(ms, font, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c", valX+(valW+PAD+RW)/2, y+(RH-8)/2, 0xFFFFFF);
                { final int fy=y;
                  zones.add(new Zone(valX,fy,valW+PAD+RW,RH,()->{
                      saveExpanded(); HotKeybindMod.config.macros.remove(idx);
                      sExpanded=-1; MacroConfig.save(); HotKeybindMod.rebuildMacros();
                      minecraft.setScreen(new MacrosScreen(parent)); })); }
                y+=RH+RG;
            }
        }

        // ---- Добавить макрос ----
        fillRow(ms, y, COL_ROW_BG);
        font.draw(ms, "\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043c\u0430\u043a\u0440\u043e\u0441", PAD, y+(RH-8)/2, 0xFFFFFF);
        fillBtn(ms, valX, y, valW+PAD+RW, RH, COL_BTN, mx, my);
        drawCenteredString(ms, font, "\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043c\u0430\u043a\u0440\u043e\u0441", valX+(valW+PAD+RW)/2, y+(RH-8)/2, 0xFFFFFF);
        { final int fy=y; zones.add(new Zone(valX,fy,valW+PAD+RW,RH,()->{
            saveExpanded(); HotKeybindMod.config.macros.add(new ConfigOptions.ConfigMacro());
            sExpanded=HotKeybindMod.config.macros.size()-1;
            MacroConfig.save(); HotKeybindMod.rebuildMacros();
            minecraft.setScreen(new MacrosScreen(parent)); })); }
        y+=RH+RG;

        // ---- Bottom buttons ----
        int bw=150, bh=20;
        int by=height-26;
        fillBtn(ms, width/2-155, by, bw, bh, COL_BTN, mx, my);
        drawCenteredString(ms, font, "\u041e\u0442\u043c\u0435\u043d\u0430", width/2-155+bw/2, by+(bh-8)/2, 0xFFFFFF);
        zones.add(new Zone(width/2-155,by,bw,bh,()->{ resetPersist(); minecraft.setScreen(parent); }));

        fillBtn(ms, width/2+5, by, bw, bh, COL_BTN, mx, my);
        drawCenteredString(ms, font, "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c", width/2+5+bw/2, by+(bh-8)/2, 0xFFFFFF);
        zones.add(new Zone(width/2+5,by,bw,bh,()->{
            saveExpanded(); MacroConfig.save(); HotKeybindMod.rebuildMacros();
            resetPersist(); minecraft.setScreen(parent); }));

        // Draw text fields on top
        if (cmdField!=null) cmdField.render(ms,mx,my,pt);
        if (delayField!=null) delayField.render(ms,mx,my,pt);
    }

    // Draws a sub-row with label + optional clickable value btn + reset btn.
    // If valLabel is null, leaves space for a TextFieldWidget.
    // Returns next y.
    private int drawSubRow(MatrixStack ms, int mx, int my, List<Zone> zones,
            int y, String label, int valX, int valW, int rstX, int RW,
            String valLabel, Runnable valAction) {
        fillRow(ms, y, COL_ROW_BG);
        font.draw(ms, label, PAD+20, y+(RH-8)/2, 0xFFFFFF);
        if (valLabel!=null) {
            fillBtn(ms, valX, y, valW, RH, COL_BTN, mx, my);
            drawCenteredString(ms, font, valLabel, valX+valW/2, y+(RH-8)/2, 0xFFFFFF);
            if (valAction!=null) {
                final int fy=y;
                zones.add(new Zone(valX,fy,valW,RH,valAction));
            }
        }
        // Reset button drawn for all sub-rows (caller adds zone if needed)
        fillBtn(ms, rstX, y, RW, RH, COL_RST, mx, my);
        drawCenteredString(ms, font, "\u0421\u0431\u0440\u043e\u0441", rstX+RW/2, y+(RH-8)/2, 0xFFFFFF);
        return y+RH+RG;
    }

    private void saveExpanded() {
        if (sExpanded<0||sExpanded>=HotKeybindMod.config.macros.size()) return;
        ConfigOptions.ConfigMacro m=HotKeybindMod.config.macros.get(sExpanded);
        if (cmdField!=null) m.command=cmdField.getValue();
        if (delayField!=null) { try{m.delay=Integer.parseInt(delayField.getValue());}catch(NumberFormatException e){} }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (listening>0 && btn!=0 && sExpanded>=0) {
            ConfigOptions.ConfigMacro m=HotKeybindMod.config.macros.get(sExpanded);
            String name=InputMappings.Type.MOUSE.getOrCreate(btn).getName();
            if(listening==1) m.keyName=name; else m.keyModName=name;
            listening=0; return true;
        }
        // Update TextField focus based on click location
        if (cmdField != null) cmdField.setFocus(cmdField.isMouseOver(mx, my));
        if (delayField != null) delayField.setFocus(delayField.isMouseOver(mx, my));

        if (btn==0) {
            for (Zone z : zones) { if (z.hit(mx,my)) { z.action.run(); return true; } }
        }
        return super.mouseClicked(mx,my,btn);
    }

    @Override public boolean keyPressed(int kc, int sc, int mod) {
        if (listening>0 && sExpanded>=0) {
            ConfigOptions.ConfigMacro m=HotKeybindMod.config.macros.get(sExpanded);
            if (kc==GLFW.GLFW_KEY_ESCAPE) {
                if(listening==1) m.keyName="key.keyboard.unknown";
                else m.keyModName="key.keyboard.unknown";
                listening=0; return true;
            }
            String name=InputMappings.getKey(kc,sc).getName();
            if(listening==1) m.keyName=name; else m.keyModName=name;
            listening=0; return true;
        }
        if (cmdField!=null&&cmdField.keyPressed(kc,sc,mod)) return true;
        if (delayField!=null&&delayField.keyPressed(kc,sc,mod)) return true;
        return super.keyPressed(kc,sc,mod);
    }

    @Override public boolean mouseScrolled(double mx,double my,double delta) {
        sScrollOffset=Math.max(0,sScrollOffset-(int)(delta*12));
        minecraft.setScreen(new MacrosScreen(parent)); return true;
    }
    @Override public boolean charTyped(char c,int m) {
        if(cmdField!=null&&cmdField.charTyped(c,m)) return true;
        if(delayField!=null&&delayField.charTyped(c,m)) return true;
        return super.charTyped(c,m);
    }
    @Override public void tick() {
        super.tick();
        if(cmdField!=null) cmdField.tick();
        if(delayField!=null) delayField.tick();
    }
}
