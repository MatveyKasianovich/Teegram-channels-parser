package com.example.demo.service;

import com.example.demo.entity.ChannelEntity;
import com.example.demo.entity.PostEntity;
import com.example.demo.repository.ChannelRepository;
import com.example.demo.repository.PostRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class TelegramParserService {

    private static final Logger log = Logger.getLogger(TelegramParserService.class.getName());

    private final PostRepository postRepository;
    private final ChannelRepository channelRepository;

    public TelegramParserService(PostRepository postRepository, ChannelRepository channelRepository) {
        this.postRepository = postRepository;
        this.channelRepository = channelRepository;
    }

    @Transactional
    public int parseAllChannels() {
        List<ChannelEntity> channels = channelRepository.findAll();
        if (channels.isEmpty()) {
            // Инициализация каналов по умолчанию
            initDefaultChannels();
            channels = channelRepository.findAll();
        }

        int totalParsed = 0;
        for (ChannelEntity channel : channels) {
            totalParsed += parseChannel(channel.getName());
        }

        log.info("Parsed " + totalParsed + " new posts from all channels");
        return totalParsed;
    }

    private void initDefaultChannels() {
        String[][] defaultChannels = {
                {"ФаМИ ГрГУ", "famigrsu", "1"},
                {"GrSU Official", "grsu_official", "2"},
                {"ГрГУ БРСМ", "kupaly_brsm", "2"}
        };

        for (String[] ch : defaultChannels) {
            ChannelEntity channel = new ChannelEntity();
            channel.setName(ch[1]);
            channel.setUrl("https://t.me/s/" + ch[1]);
            channel.setPriority(Integer.parseInt(ch[2]));
            channelRepository.save(channel);
        }
    }

    @Transactional
    public int parseChannel(String channelName) {
        String url = "https://t.me/s/" + channelName;

        ChannelEntity channel = channelRepository.findByName(channelName)
                .orElseThrow(() -> new RuntimeException("Channel not found: " + channelName));

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            var messageElements = doc.select(".tgme_widget_message_wrap");
            int savedCount = 0;

            for (Element element : messageElements) {
                Element textElement = element.selectFirst(".tgme_widget_message_text");
                if (textElement == null) continue;

                String text = textElement.text();
                int hash = text.hashCode();

                if (!postRepository.existsByTextHash(hash)) {
                    PostEntity post = new PostEntity();
                    post.setChannel(channel);
                    post.setText(text);
                    post.setTextHash(hash);

                    String dataPost = element.select(".tgme_widget_message").attr("data-post");
                    if (dataPost.contains("/")) {
                        Long msgId = Long.parseLong(dataPost.split("/")[1]);
                        post.setTelegramId(msgId);
                    } else {
                        post.setTelegramId(0L);
                    }

                    Element timeElement = element.selectFirst("time");
                    if (timeElement != null) {
                        String isoTime = timeElement.attr("datetime");
                        try {
                            ZonedDateTime utcTime = ZonedDateTime.parse(isoTime);
                            ZonedDateTime moscowTime = utcTime.withZoneSameInstant(ZoneId.of("Europe/Moscow"));
                            post.setPublishedAt(moscowTime.toLocalDateTime());
                        } catch (Exception e) {
                            post.setPublishedAt(LocalDateTime.now(ZoneId.of("Europe/Moscow")));
                        }
                    } else {
                        post.setPublishedAt(LocalDateTime.now(ZoneId.of("Europe/Moscow")));
                    }

                    post.setCreatedAt(LocalDateTime.now(ZoneId.of("Europe/Moscow")));
                    post.setProcessed(false);

                    postRepository.save(post);
                    savedCount++;
                }
            }

            channel.setLastParsedAt(LocalDateTime.now(ZoneId.of("Europe/Moscow")));
            channelRepository.save(channel);

            log.info("Parsed " + savedCount + " new posts from " + channelName);
            return savedCount;

        } catch (IOException e) {
            log.severe("Error parsing " + channelName + ": " + e.getMessage());
            return 0;
        }
    }
}