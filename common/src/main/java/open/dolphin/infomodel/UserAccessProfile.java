package open.dolphin.infomodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * Web client 管理画面向けの職員アクセスプロファイル。
 * 既存の d_users へ非互換の列追加を避けるため、補助テーブルとして保持する。
 */
@Entity
@Table(name = "d_user_access_profile")
public class UserAccessProfile implements Serializable {

    @Id
    @Column(name = "user_pk", nullable = false)
    private Long userPk;

    /**
     * Sex code: "M" (male), "F" (female), "O" (other/unknown).
     */
    @Column(name = "sex", length = 1)
    private String sex;

    /**
     * Staff role label (e.g. doctor/nurse/office/clerk/reception/admin).
     * This is a UI-level metadata and does not replace d_roles authorization.
     */
    @Column(name = "staff_role", length = 32)
    private String staffRole;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UserAccessProfile() {
    }

    public Long getUserPk() {
        return userPk;
    }

    public void setUserPk(Long userPk) {
        this.userPk = userPk;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getStaffRole() {
        return staffRole;
    }

    public void setStaffRole(String staffRole) {
        this.staffRole = staffRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

