package com.mss301.account;

import com.mss301.account.entity.StoreAccount;
import com.mss301.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

// [TODO-3.1: seed 5 tài khoản, mật khẩu BCrypt]
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            String encoded = passwordEncoder.encode("@1");
            List<StoreAccount> accounts = List.of(
                StoreAccount.builder().username("adminsys").email("admin@drawingpen.com.vn").password(encoded).role(1).isActive(true).build(),
                StoreAccount.builder().username("staff02").email("staff02@drawingpen.com.vn").password(encoded).role(2).isActive(true).build(),
                StoreAccount.builder().username("member01").email("member01@drawingpen.com.vn").password(encoded).role(3).isActive(true).build(),
                StoreAccount.builder().username("member02").email("member02@drawingpen.com.vn").password(encoded).role(3).isActive(false).build(),
                StoreAccount.builder().username("staff01").email("staff01@drawingpen.com.vn").password(encoded).role(2).isActive(false).build()
            );
            accountRepository.saveAll(accounts);
            log.info("Seeded {} accounts", accounts.size());
        }
    }
}
