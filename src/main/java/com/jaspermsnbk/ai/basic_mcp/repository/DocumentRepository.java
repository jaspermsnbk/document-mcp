package com.jaspermsnbk.ai.basic_mcp.repository;

import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Optional;

public interface DocumentRepository
        extends ListCrudRepository<PdfDocument, Long>,
                PagingAndSortingRepository<PdfDocument, Long> {

    Optional<PdfDocument> findBySha256Hash(String sha256Hash);
}
