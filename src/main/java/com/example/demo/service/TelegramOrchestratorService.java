package com.example.demo.service;

import com.example.demo.dto.DTOtoFRONT;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.logging.Logger;

@Service
public class TelegramOrchestratorService {

    private static final Logger log = Logger.getLogger(TelegramOrchestratorService.class.getName());

    private final TelegramParserService parserService;
    private final PostProcessingService processingService;

    public TelegramOrchestratorService(TelegramParserService parserService,
                                       PostProcessingService processingService) {
        this.parserService = parserService;
        this.processingService = processingService;
    }

    @Transactional
    public ParseResult parseAndProcess() {
        log.info("Starting full pipeline: parse + AI processing");
        long startTime = System.currentTimeMillis();

        int parsedCount = parserService.parseAllChannels();
        int processedCount = processingService.processUnprocessedPosts();
        List<DTOtoFRONT> events = processingService.getLastProcessedEvents(30);

        long duration = System.currentTimeMillis() - startTime;

        log.info(String.format("Pipeline completed: %d parsed, %d processed in %d ms",
                parsedCount, processedCount, duration));

        return new ParseResult(parsedCount, processedCount, events, duration);
    }

    public static class ParseResult {
        private final int parsedCount;
        private final int processedCount;
        private final List<DTOtoFRONT> events;
        private final long durationMs;

        public ParseResult(int parsedCount, int processedCount, List<DTOtoFRONT> events, long durationMs) {
            this.parsedCount = parsedCount;
            this.processedCount = processedCount;
            this.events = events;
            this.durationMs = durationMs;
        }

        public int getParsedCount() { return parsedCount; }
        public int getProcessedCount() { return processedCount; }
        public List<DTOtoFRONT> getEvents() { return events; }
        public long getDurationMs() { return durationMs; }
    }
}