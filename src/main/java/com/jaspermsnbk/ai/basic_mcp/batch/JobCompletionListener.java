package com.jaspermsnbk.ai.basic_mcp.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class JobCompletionListener implements JobExecutionListener {

    private final JdbcTemplate jdbcTemplate;

    public JobCompletionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            Long stagingId = jobExecution.getJobParameters().getLong("stagingId");
            String errorMsg = jobExecution.getAllFailureExceptions().stream()
                .map(t -> t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName())
                .collect(Collectors.joining("; "));
            jdbcTemplate.update(
                "UPDATE pdf_staging SET status = 'FAILED', error_msg = ?, completed_at = NOW() WHERE id = ?",
                errorMsg, stagingId
            );
        }
    }
}
