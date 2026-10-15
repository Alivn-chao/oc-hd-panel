package dev.bec.hdpanel;

import com.google.gson.*;
import dev.bec.hdpanel.client.BindingPainter;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class BindingPainterTest {
    @Test public void directionsUseRequestedNamesAndOriginalOcNumbers(){
        assertArrayEquals(new int[]{1,0,3,2,5,4},BindingPainter.SIDES);
        assertEquals("上",BindingPainter.sideName("1"));assertEquals("下",BindingPainter.sideName("0"));
        assertEquals("南",BindingPainter.sideName("3"));assertEquals("北",BindingPainter.sideName("2"));
        assertEquals("东",BindingPainter.sideName("5"));assertEquals("西",BindingPainter.sideName("4"));
    }
    @Test public void pageIs1080pAndRendersIncompleteDraftAndDirectionSelection()throws Exception{
        JsonArray fields=new JsonArray();List<String> values=new ArrayList<String>();
        String[] labels={"公共 · 主网供液ME接口","公共 · 样板工坊ME接口（可空）","公共 · 配方目录","组1 · 约束场","组1 · 公共韦门","组1 · 主网侧方向","组1 · 子网侧方向","组1 · 蜂群装入红石"};
        for(int i=0;i<labels.length;i++){JsonObject f=new JsonObject();f.addProperty("label",labels[i]);f.addProperty("kind",i==5||i==6?"side":"uuid");fields.add(f);values.add(i==2?"/home/bec_v2/recipes":i==5?"4":i==6?"5":i==0?"01234567-1234-4567-89ab-123456789abc":i==3?"01234":"");}
        BufferedImage image=BindingPainter.paint(fields,values,0,3,5,5,true,5,"草稿已暂存，可退出查地址，回来继续填写。",false);
        assertEquals(1920,image.getWidth());assertEquals(1080,image.getHeight());
        ImageIO.write(image,"png",new File("build/bindings-preview.png"));
    }
}
