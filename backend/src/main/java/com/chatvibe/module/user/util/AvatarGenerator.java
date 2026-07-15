package com.chatvibe.module.user.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 默认头像生成器：根据昵称首字母+随机色生成RNG头像
 *
 * @author Alu
 * @date 2026-07-15
 */
public class AvatarGenerator {
    // 预设背景色板
    private static final Color[] COLORS = {
            new Color(37, 99, 235),    // 蓝
            new Color(124, 58, 237),   // 紫
            new Color(219, 39, 119),   // 粉
            new Color(220, 38, 38),    // 红
            new Color(234, 88, 12),    // 橙
            new Color(22, 163, 74),    // 绿
            new Color(8, 145, 178),    // 青
    };

    /**
     * 生成头像 PNG 字节数组
     * @param nickname 昵称（取首字符）
     * @return PNG 图片字节
     */
    public static byte[] generate(String nickname) throws IOException {
        int size = 200;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景色（基于昵称 hash 选色）
        int colorIndex = Math.abs(nickname.hashCode()) % COLORS.length;
        g2d.setColor(COLORS[colorIndex]);
        g2d.fillRect(0, 0, size, size);

        // 绘制首字符（白色，居中）
        String firstChar = nickname.substring(0, 1).toUpperCase();
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 100));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (size - fm.stringWidth(firstChar)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(firstChar, x, y);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
