package com.shortlink.linkapi.service.impl;

import com.shortlink.linkapi.component.CodeGenerator;
import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.dto.LinkResponse;
import com.shortlink.linkapi.entity.LinkEntity;
import com.shortlink.linkapi.repository.LinkRepository;
import com.shortlink.linkapi.service.LinkService;
import com.shortlink.linkapi.utils.AuthenticationUtils;
import com.shortlink.linkapi.validation.LinkValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LinkServiceImpl implements LinkService {

    private final LinkRepository linkRepository;
    private final LinkValidator linkValidator;
    private final CodeGenerator codeGenerator;

    @Override
    public LinkResponse createLink(LinkRequest linkRequest) {

        linkValidator.validateRequest(linkRequest);

        if (linkRequest.getCustomCode() == null) {
            linkRequest.setCustomCode(codeGenerator.next());
        }

        LinkEntity linkEntity = LinkEntity.builder()
                .code(linkRequest.getCustomCode())
                .userId(AuthenticationUtils.requiredCurrentUserId())
                .createdAt(OffsetDateTime.now())
                .expiresAt(linkRequest.getExpiresAt())
                .isActive(true)
                .targetUrl(linkRequest.getTargetUrl()).build();

         linkRepository.save(linkEntity);

        return new LinkResponse(
                linkEntity.getCode(),
                null,
                linkEntity.getTargetUrl(),
                true,
                linkEntity.getCreatedAt(),
                linkEntity.getExpiresAt()
        );
    }
}
