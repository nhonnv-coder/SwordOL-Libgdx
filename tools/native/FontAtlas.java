import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Generates raster UI glyphs with the platform's logical sans font; ships no system font file. */
public final class FontAtlas {
  public static void main(String[] args) throws Exception {
    Path output=Paths.get(args[0]);Files.createDirectories(output);
    Font font=new Font(Font.SANS_SERIF,Font.PLAIN,18);
    ArrayList<Integer> points=new ArrayList<>();
    for(int c=32;c<=0x24f;c++)if(!Character.isISOControl(c)&&font.canDisplay(c))points.add(c);
    for(int c=0x1e00;c<=0x1eff;c++)if(font.canDisplay(c))points.add(c);
    int cell=32,width=1024,height=((points.size()+31)/32)*cell;
    BufferedImage atlas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=atlas.createGraphics();g.setFont(font);g.setColor(Color.WHITE);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    FontMetrics metrics=g.getFontMetrics();
    StringBuilder fnt=new StringBuilder("info face=\"SwordOL UI\" size=18 bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0\n");
    fnt.append("common lineHeight=24 base=").append(metrics.getAscent()).append(" scaleW=").append(width)
        .append(" scaleH=").append(height).append(" pages=1 packed=0\npage id=0 file=\"native-ui.png\"\nchars count=").append(points.size()).append('\n');
    for(int i=0;i<points.size();i++) {
      int c=points.get(i),x=(i%32)*cell+1,y=(i/32)*cell+1;
      GlyphVector glyph=font.createGlyphVector(g.getFontRenderContext(),new char[]{(char)c});
      Rectangle b=glyph.getPixelBounds(g.getFontRenderContext(),0,0);
      if(b.width>30||b.height>30)throw new IOException("Glyph exceeds atlas cell");
      g.drawGlyphVector(glyph,x-b.x,y-b.y);
      fnt.append("char id=").append(c).append(" x=").append(x).append(" y=").append(y)
          .append(" width=").append(Math.max(1,b.width)).append(" height=").append(Math.max(1,b.height))
          .append(" xoffset=").append(b.x).append(" yoffset=").append(metrics.getAscent()+b.y)
          .append(" xadvance=").append(metrics.charWidth(c)).append(" page=0 chnl=15\n");
    }
    g.dispose();ImageIO.write(atlas,"png",output.resolve("native-ui.png").toFile());
    Files.write(output.resolve("native-ui.fnt"),fnt.toString().getBytes(StandardCharsets.UTF_8));
    System.out.println("Raster UI glyphs: "+points.size());
  }
}
