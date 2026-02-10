package com.tuya.smartai.demo;

/**
 * 文本数据对象
 * 用于存储从JSON中解析出的文本数据
 */
public class TextData {
    private String bizId;
    private String bizType;
    private int eof;
    private String text;
    private String imageUrl;
    
    public TextData() {
    }
    
    public TextData(String bizId, String bizType, int eof, String text) {
        this.bizId = bizId;
        this.bizType = bizType;
        this.eof = eof;
        this.text = text;
    }
    
    public String getBizId() {
        return bizId;
    }
    
    public void setBizId(String bizId) {
        this.bizId = bizId;
    }
    
    public String getBizType() {
        return bizType;
    }
    
    public void setBizType(String bizType) {
        this.bizType = bizType;
    }
    
    public int getEof() {
        return eof;
    }
    
    public void setEof(int eof) {
        this.eof = eof;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }
    
    @Override
    public String toString() {
        return "TextData{" +
                "bizId='" + bizId + '\'' +
                ", bizType='" + bizType + '\'' +
                ", eof=" + eof +
                ", text='" + text + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}
