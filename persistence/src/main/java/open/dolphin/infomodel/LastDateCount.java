/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package open.dolphin.infomodel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 *
 * @author kazushi
 */
public class LastDateCount extends InfoModel {
    
    // システム登録日->初診日として使用
    private LocalDate created;
    
    // 病名数
    private long diagnosisCount;
    
    // アクティブ病名数
    private long activeDiagnosisCount;
    
    // カル枚数
    private long docCount;
    
    // 最終カルテ記録日
    private LocalDate lastDocDate;
    
    // 検査数
    private long labCount;
    
    // 最終検査結果日
    private String lastLabDate;
    
    // 画像及びシェーマ数
    private long imageCount;
    
    // 最終画像日
    private LocalDate lastImageDate;

    public Date getCreated() {
        return created == null
                ? null
                : Date.from(created.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public void setCreated(Date registeredDate) {
        this.created = registeredDate == null
                ? null
                : Instant.ofEpochMilli(registeredDate.getTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
    }

    public LocalDate getCreatedLocalDate() {
        return created;
    }

    public void setCreatedLocalDate(LocalDate created) {
        this.created = created;
    }

    public long getDocCount() {
        return docCount;
    }

    public void setDocCount(long docCount) {
        this.docCount = docCount;
    }

    public Date getLastDocDate() {
        return lastDocDate == null
                ? null
                : Date.from(lastDocDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public void setLastDocDate(Date lastDocDate) {
        this.lastDocDate = lastDocDate == null
                ? null
                : Instant.ofEpochMilli(lastDocDate.getTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
    }

    public LocalDate getLastDocLocalDate() {
        return lastDocDate;
    }

    public void setLastDocLocalDate(LocalDate lastDocDate) {
        this.lastDocDate = lastDocDate;
    }

    public long getLabCount() {
        return labCount;
    }

    public void setLabCount(long labCount) {
        this.labCount = labCount;
    }

    public String getLastLabDate() {
        return lastLabDate;
    }

    public void setLastLabDate(String lastSampleDate) {
        this.lastLabDate = lastSampleDate;
    }

    public long getImageCount() {
        return imageCount;
    }

    public void setImageCount(long imageCount) {
        this.imageCount = imageCount;
    }

    public Date getLastImageDate() {
        return lastImageDate == null
                ? null
                : Date.from(lastImageDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public void setLastImageDate(Date lastImageDate) {
        this.lastImageDate = lastImageDate == null
                ? null
                : Instant.ofEpochMilli(lastImageDate.getTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
    }

    public LocalDate getLastImageLocalDate() {
        return lastImageDate;
    }

    public void setLastImageLocalDate(LocalDate lastImageDate) {
        this.lastImageDate = lastImageDate;
    }

    public long getDiagnosisCount() {
        return diagnosisCount;
    }

    public void setDiagnosisCount(long diagnosisCount) {
        this.diagnosisCount = diagnosisCount;
    }

    public long getActiveDiagnosisCount() {
        return activeDiagnosisCount;
    }

    public void setActiveDiagnosisCount(long activeDiagnosisCount) {
        this.activeDiagnosisCount = activeDiagnosisCount;
    }
}
