package com.jaspermsnbk.ai.basic_mcp.repository;

import com.jaspermsnbk.ai.basic_mcp.domain.PdfStaging;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface PdfStagingRepository extends ListCrudRepository<PdfStaging, Long> {
    List<PdfStaging> findBySha256Hash(String sha256Hash);
}
