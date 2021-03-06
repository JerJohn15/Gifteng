package com.venefica.service.dto;

import com.venefica.model.Image;
import com.venefica.model.ImageType;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * Image data transfer object.
 *
 * @author Sviatoslav Grebenchukov
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ImageDto extends DtoBase {

    private static final String BASE_PATH = "/images/";
    private static final String NOIMAGE = "noimage";
    
    // out
    private Long id;
    // in
    @NotNull
    private ImageType imgType;
    // in
    @NotNull
    private byte[] data;
    // out
    private String url;

    public static String imageUrl(Image image) {
        return image != null ? BASE_PATH + "img" + image.getId().toString() : null;
    }
    
    // required for JAX-WS
    public ImageDto() {
    }

    /**
     * Converts Image into ImageDTO. Note that the data and image type are intentionally
     * not copied as this would create a huge response. Instead use the url field
     * to get the image.
     * 
     * @param image 
     */
    public ImageDto(Image image) {
        this.id = image.getId();
        this.url = imageUrl(image);
    }
    
    public ImageDto(ImageType imgType, byte[] data) {
        this.imgType = imgType;
        this.data = data;
    }

    public boolean isValid() {
        return imgType != null && data != null;
    }

    public Image toImage() {
        return new Image(imgType, data);
    }

    public void updateImage(Image image) {
        image.setImgType(imgType);
        image.setData(data);
    }
    
    public void updateImageDto(Image image) {
        imgType = image.getImgType();
        data = image.getData();
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ImageType getImgType() {
        return imgType;
    }

    public void setImgType(ImageType imgType) {
        this.imgType = imgType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
