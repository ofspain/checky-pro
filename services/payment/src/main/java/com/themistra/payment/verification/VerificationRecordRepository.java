package com.themistra.payment.verification;

import org.springframework.data.jpa.repository.JpaRepository;

interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {
}
