package com.mss301.account.entity;

import jakarta.persistence.*;
import lombok.*;

// [TODO-3.1: entity StoreAccounts (@Builder.Default cho isActive)]
@Entity
@Table(name = "StoreAccounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AccountID")
    private Integer accountId;

    @Column(name = "Username", nullable = false, length = 20)
    private String username;

    @Column(name = "Email", nullable = false, length = 100)
    private String email;

    @Column(name = "Password", nullable = false, length = 100)
    private String password;

    @Column(name = "Role")
    private Integer role; // 1=Admin, 2=Staff, 3=Member

    @Column(name = "IsActive", columnDefinition = "BIT DEFAULT 1")
    @Builder.Default            // nếu thiếu, builder sẽ bỏ qua giá trị mặc định = true
    private Boolean isActive = true;
}
