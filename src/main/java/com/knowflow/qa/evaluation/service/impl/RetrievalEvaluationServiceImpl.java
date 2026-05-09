package com.knowflow.qa.evaluation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.integration.search.KnowledgeSearchClient;
import com.knowflow.integration.search.model.KnowledgeSearchHit;
import com.knowflow.integration.search.model.QueryVariantHit;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.qa.evaluation.dto.CreateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.dto.RunRetrievalEvaluationRequest;
import com.knowflow.qa.evaluation.dto.UpdateRetrievalEvalCaseRequest;
import com.knowflow.qa.evaluation.entity.RetrievalEvalCaseEntity;
import com.knowflow.qa.evaluation.entity.RetrievalEvalResultEntity;
import com.knowflow.qa.evaluation.entity.RetrievalEvalRunEntity;
import com.knowflow.qa.evaluation.mapper.RetrievalEvalCaseMapper;
import com.knowflow.qa.evaluation.mapper.RetrievalEvalResultMapper;
import com.knowflow.qa.evaluation.mapper.RetrievalEvalRunMapper;
import com.knowflow.qa.evaluation.service.RetrievalEvaluationService;
import com.knowflow.qa.evaluation.vo.RetrievalEvalCaseVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalHitVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalResultVO;
import com.knowflow.qa.evaluation.vo.RetrievalEvalRunVO;
import com.knowflow.qa.vo.QueryVariantVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RetrievalEvaluationServiceImpl implements RetrievalEvaluationService {

    private static final String EXPECTED_SUCCESS = "SUCCESS";
    private static final String EXPECTED_NO_HIT = "NO_HIT";
    private static final TypeReference<List<RetrievalEvalHitVO>> HIT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<QueryVariantVO>> QUERY_VARIANT_LIST_TYPE = new TypeReference<>() {
    };

    private final RetrievalEvalCaseMapper caseMapper;
    private final RetrievalEvalRunMapper runMapper;
    private final RetrievalEvalResultMapper resultMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSearchClient knowledgeSearchClient;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final int defaultTopK;
    private final double minRecallScore;
    private final double strongLexicalThreshold;
    private final double lowConfidenceHandoffThreshold;

    public RetrievalEvaluationServiceImpl(RetrievalEvalCaseMapper caseMapper,
                                          RetrievalEvalRunMapper runMapper,
                                          RetrievalEvalResultMapper resultMapper,
                                          KnowledgeBaseMapper knowledgeBaseMapper,
                                          KnowledgeDocumentMapper knowledgeDocumentMapper,
                                          KnowledgeSearchClient knowledgeSearchClient,
                                          CurrentUserProvider currentUserProvider,
                                          ObjectMapper objectMapper,
                                          @Value("${knowflow.qa.top-k:5}") int defaultTopK,
                                          @Value("${knowflow.qa.min-recall-score:0.65}") double minRecallScore,
                                          @Value("${knowflow.qa.strong-lexical-threshold:0.82}") double strongLexicalThreshold,
                                          @Value("${knowflow.qa.low-confidence-handoff-threshold:0.50}") double lowConfidenceHandoffThreshold) {
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSearchClient = knowledgeSearchClient;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.defaultTopK = defaultTopK;
        this.minRecallScore = minRecallScore;
        this.strongLexicalThreshold = strongLexicalThreshold;
        this.lowConfidenceHandoffThreshold = lowConfidenceHandoffThreshold;
    }

    @Override
    @Transactional
    public RetrievalEvalCaseVO createCase(CreateRetrievalEvalCaseRequest request) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        ensureKnowledgeBase(tenantId, request.getKnowledgeBaseId());
        KnowledgeDocumentEntity expectedDocument = resolveExpectedDocument(
                tenantId,
                request.getKnowledgeBaseId(),
                request.getExpectedDocumentId()
        );

        RetrievalEvalCaseEntity entity = new RetrievalEvalCaseEntity();
        entity.setTenantId(tenantId);
        entity.setKnowledgeBaseId(request.getKnowledgeBaseId());
        entity.setCaseName(request.getCaseName());
        entity.setQuestionText(request.getQuestionText());
        entity.setExpectedStatus(normalizeExpectedStatus(request.getExpectedStatus()));
        entity.setExpectedDocumentId(request.getExpectedDocumentId());
        entity.setExpectedDocumentName(resolveExpectedDocumentName(request.getExpectedDocumentName(), expectedDocument));
        entity.setExpectedKeywords(writeKeywords(request.getExpectedKeywords()));
        entity.setTopK(resolveTopK(request.getTopK()));
        entity.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        entity.setRemark(request.getRemark());
        caseMapper.insert(entity);
        return toCaseVO(entity, loadKnowledgeBaseNames(tenantId, List.of(entity.getKnowledgeBaseId())));
    }

    @Override
    public PageResponse<RetrievalEvalCaseVO> pageCases(Integer pageNo,
                                                       Integer pageSize,
                                                       Long knowledgeBaseId,
                                                       String keyword,
                                                       Boolean enabled) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Page<RetrievalEvalCaseEntity> page = caseMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<RetrievalEvalCaseEntity>()
                        .eq(RetrievalEvalCaseEntity::getTenantId, tenantId)
                        .eq(knowledgeBaseId != null, RetrievalEvalCaseEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(enabled != null, RetrievalEvalCaseEntity::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(RetrievalEvalCaseEntity::getCaseName, keyword)
                                .or()
                                .like(RetrievalEvalCaseEntity::getQuestionText, keyword))
                        .orderByDesc(RetrievalEvalCaseEntity::getCreatedAt)
        );
        Map<Long, String> knowledgeBaseNames = loadKnowledgeBaseNames(
                tenantId,
                page.getRecords().stream().map(RetrievalEvalCaseEntity::getKnowledgeBaseId).toList()
        );
        List<RetrievalEvalCaseVO> records = page.getRecords().stream()
                .map(entity -> toCaseVO(entity, knowledgeBaseNames))
                .toList();
        return PageResponse.of((int) page.getCurrent(), (int) page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional
    public RetrievalEvalCaseVO updateCase(Long id, UpdateRetrievalEvalCaseRequest request) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        RetrievalEvalCaseEntity entity = getCaseForTenant(tenantId, id);
        KnowledgeDocumentEntity expectedDocument = resolveExpectedDocument(
                tenantId,
                entity.getKnowledgeBaseId(),
                request.getExpectedDocumentId()
        );

        entity.setCaseName(request.getCaseName());
        entity.setQuestionText(request.getQuestionText());
        entity.setExpectedStatus(normalizeExpectedStatus(request.getExpectedStatus()));
        entity.setExpectedDocumentId(request.getExpectedDocumentId());
        entity.setExpectedDocumentName(resolveExpectedDocumentName(request.getExpectedDocumentName(), expectedDocument));
        entity.setExpectedKeywords(writeKeywords(request.getExpectedKeywords()));
        entity.setTopK(resolveTopK(request.getTopK()));
        entity.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        entity.setRemark(request.getRemark());
        caseMapper.updateById(entity);
        return toCaseVO(entity, loadKnowledgeBaseNames(tenantId, List.of(entity.getKnowledgeBaseId())));
    }

    @Override
    @Transactional
    public void deleteCase(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        getCaseForTenant(tenantId, id);
        caseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public RetrievalEvalRunVO run(RunRetrievalEvaluationRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Long tenantId = currentUser.tenantId();
        List<RetrievalEvalCaseEntity> cases = loadRunnableCases(tenantId, request);
        if (cases.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "No enabled evaluation cases matched the request");
        }

        RetrievalEvalRunEntity run = new RetrievalEvalRunEntity();
        run.setTenantId(tenantId);
        run.setRunNo(CodeGenerator.prefixedCode("QAE"));
        run.setKnowledgeBaseId(request.getKnowledgeBaseId());
        run.setTotalCases(cases.size());
        run.setPassedCases(0);
        run.setFailedCases(0);
        run.setPassRate(BigDecimal.ZERO);
        run.setRecallAtK(BigDecimal.ZERO);
        run.setTop1HitRate(BigDecimal.ZERO);
        run.setNoHitAccuracy(BigDecimal.ZERO);
        run.setAvgTopScore(BigDecimal.ZERO);
        run.setAvgTopLexicalScore(BigDecimal.ZERO);
        run.setAvgTopVectorScore(BigDecimal.ZERO);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        List<RetrievalEvalResultEntity> results = new ArrayList<>();
        for (RetrievalEvalCaseEntity evalCase : cases) {
            RetrievalEvalResultEntity result = evaluateCase(tenantId, run.getId(), evalCase, request.getTopK());
            resultMapper.insert(result);
            results.add(result);
        }

        applyRunMetrics(run, results);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        return toRunVO(run, true);
    }

    @Override
    public PageResponse<RetrievalEvalRunVO> pageRuns(Integer pageNo, Integer pageSize, Long knowledgeBaseId) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Page<RetrievalEvalRunEntity> page = runMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<RetrievalEvalRunEntity>()
                        .eq(RetrievalEvalRunEntity::getTenantId, tenantId)
                        .eq(knowledgeBaseId != null, RetrievalEvalRunEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(RetrievalEvalRunEntity::getCreatedAt)
        );
        List<RetrievalEvalRunVO> records = page.getRecords().stream()
                .map(entity -> toRunVO(entity, false))
                .toList();
        return PageResponse.of((int) page.getCurrent(), (int) page.getSize(), page.getTotal(), records);
    }

    @Override
    public RetrievalEvalRunVO getRun(Long id) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        RetrievalEvalRunEntity entity = getRunForTenant(tenantId, id);
        return toRunVO(entity, true);
    }

    @Override
    public List<RetrievalEvalResultVO> listResults(Long runId) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        getRunForTenant(tenantId, runId);
        return loadResultVOs(tenantId, runId);
    }

    private List<RetrievalEvalCaseEntity> loadRunnableCases(Long tenantId, RunRetrievalEvaluationRequest request) {
        List<Long> caseIds = request.getCaseIds() == null
                ? List.of()
                : request.getCaseIds().stream().filter(Objects::nonNull).distinct().toList();
        if (request.getKnowledgeBaseId() != null) {
            ensureKnowledgeBase(tenantId, request.getKnowledgeBaseId());
        }
        return caseMapper.selectList(
                new LambdaQueryWrapper<RetrievalEvalCaseEntity>()
                        .eq(RetrievalEvalCaseEntity::getTenantId, tenantId)
                        .eq(RetrievalEvalCaseEntity::getEnabled, 1)
                        .eq(request.getKnowledgeBaseId() != null, RetrievalEvalCaseEntity::getKnowledgeBaseId, request.getKnowledgeBaseId())
                        .in(!caseIds.isEmpty(), RetrievalEvalCaseEntity::getId, caseIds)
                        .orderByAsc(RetrievalEvalCaseEntity::getId)
        );
    }

    private RetrievalEvalResultEntity evaluateCase(Long tenantId,
                                                   Long runId,
                                                   RetrievalEvalCaseEntity evalCase,
                                                   Integer requestTopK) {
        int topK = resolveTopK(requestTopK == null ? evalCase.getTopK() : requestTopK);
        List<QueryVariantVO> queryVariants = knowledgeSearchClient.explainQuery(evalCase.getQuestionText()).stream()
                .map(this::toQueryVariantVO)
                .toList();
        List<KnowledgeSearchHit> rawHits = knowledgeSearchClient.search(
                tenantId,
                evalCase.getKnowledgeBaseId(),
                evalCase.getQuestionText(),
                topK
        );
        List<RetrievalEvalHitVO> hits = rawHits.stream()
                .map(this::toHitVO)
                .toList();

        String expectedStatus = normalizeExpectedStatus(evalCase.getExpectedStatus());
        String actualStatus = hasReliableHit(rawHits) ? EXPECTED_SUCCESS : EXPECTED_NO_HIT;
        Integer hitRank = findExpectedDocumentRank(evalCase, hits);
        List<String> expectedKeywords = readKeywords(evalCase.getExpectedKeywords());
        int keywordHitCount = countKeywordHits(expectedKeywords, hits);
        boolean keywordPassed = expectedKeywords.isEmpty() || keywordHitCount >= expectedKeywords.size();
        boolean statusPassed = expectedStatus.equals(actualStatus);
        boolean documentPassed = !EXPECTED_SUCCESS.equals(expectedStatus) || !hasExpectedDocument(evalCase) || hitRank != null;
        boolean passed = statusPassed && documentPassed && keywordPassed;

        RetrievalEvalHitVO topHit = hits.isEmpty() ? null : hits.get(0);
        RetrievalEvalResultEntity result = new RetrievalEvalResultEntity();
        result.setTenantId(tenantId);
        result.setRunId(runId);
        result.setCaseId(evalCase.getId());
        result.setQuestionText(evalCase.getQuestionText());
        result.setExpectedStatus(expectedStatus);
        result.setActualStatus(actualStatus);
        result.setExpectedDocumentId(evalCase.getExpectedDocumentId());
        result.setExpectedDocumentName(evalCase.getExpectedDocumentName());
        result.setActualTopDocumentId(topHit == null ? null : topHit.getDocumentId());
        result.setActualTopDocumentName(topHit == null ? null : topHit.getDocumentName());
        result.setTopRecallScore(toBigDecimal(topHit == null ? null : topHit.getRecallScore()));
        result.setTopLexicalScore(toBigDecimal(topHit == null ? null : topHit.getLexicalScore()));
        result.setTopVectorScore(toBigDecimal(topHit == null ? null : topHit.getVectorScore()));
        result.setHitRank(hitRank);
        result.setKeywordHitCount(keywordHitCount);
        result.setKeywordTotalCount(expectedKeywords.size());
        result.setPassed(passed ? 1 : 0);
        result.setFailureReason(passed ? null : buildFailureReason(statusPassed, documentPassed, keywordPassed));
        result.setHitsJson(writeJson(hits));
        result.setQueryVariantJson(writeJson(queryVariants));
        return result;
    }

    private void applyRunMetrics(RetrievalEvalRunEntity run, List<RetrievalEvalResultEntity> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(result -> result.getPassed() != null && result.getPassed() == 1).count();
        List<RetrievalEvalResultEntity> expectedDocumentCases = results.stream()
                .filter(result -> EXPECTED_SUCCESS.equals(result.getExpectedStatus()))
                .filter(result -> result.getExpectedDocumentId() != null || StringUtils.hasText(result.getExpectedDocumentName()))
                .toList();
        List<RetrievalEvalResultEntity> noHitCases = results.stream()
                .filter(result -> EXPECTED_NO_HIT.equals(result.getExpectedStatus()))
                .toList();

        run.setPassedCases(passed);
        run.setFailedCases(total - passed);
        run.setPassRate(ratio(passed, total));
        run.setRecallAtK(ratio((int) expectedDocumentCases.stream().filter(result -> result.getHitRank() != null).count(), expectedDocumentCases.size()));
        run.setTop1HitRate(ratio((int) expectedDocumentCases.stream().filter(result -> result.getHitRank() != null && result.getHitRank() == 1).count(), expectedDocumentCases.size()));
        run.setNoHitAccuracy(ratio((int) noHitCases.stream().filter(result -> EXPECTED_NO_HIT.equals(result.getActualStatus())).count(), noHitCases.size()));
        run.setAvgTopScore(avg(results.stream().map(RetrievalEvalResultEntity::getTopRecallScore).toList()));
        run.setAvgTopLexicalScore(avg(results.stream().map(RetrievalEvalResultEntity::getTopLexicalScore).toList()));
        run.setAvgTopVectorScore(avg(results.stream().map(RetrievalEvalResultEntity::getTopVectorScore).toList()));
    }

    private boolean hasReliableHit(List<KnowledgeSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return false;
        }
        KnowledgeSearchHit topHit = hits.get(0);
        double topScore = defaultDouble(topHit.getScore());
        double topLexical = defaultDouble(topHit.getLexicalScore());
        if (topScore >= minRecallScore || topLexical >= strongLexicalThreshold) {
            return true;
        }
        if (topScore < lowConfidenceHandoffThreshold && topLexical < strongLexicalThreshold * 0.85D) {
            return false;
        }
        double avgTopThreeScore = hits.stream()
                .limit(3)
                .map(KnowledgeSearchHit::getScore)
                .mapToDouble(this::defaultDouble)
                .average()
                .orElse(0D);
        long strongEvidenceCount = hits.stream()
                .limit(3)
                .filter(hit -> defaultDouble(hit.getScore()) >= minRecallScore * 0.90D
                        || defaultDouble(hit.getLexicalScore()) >= strongLexicalThreshold * 0.90D)
                .count();
        return strongEvidenceCount >= 2 && (topScore * 0.65D + avgTopThreeScore * 0.35D) >= minRecallScore * 0.92D;
    }

    private Integer findExpectedDocumentRank(RetrievalEvalCaseEntity evalCase, List<RetrievalEvalHitVO> hits) {
        if (!hasExpectedDocument(evalCase)) {
            return null;
        }
        String expectedName = normalize(evalCase.getExpectedDocumentName());
        for (RetrievalEvalHitVO hit : hits) {
            boolean idMatched = evalCase.getExpectedDocumentId() != null
                    && evalCase.getExpectedDocumentId().equals(hit.getDocumentId());
            boolean nameMatched = StringUtils.hasText(expectedName)
                    && normalize(hit.getDocumentName()).contains(expectedName);
            if (idMatched || nameMatched) {
                return hit.getRankNo();
            }
        }
        return null;
    }

    private int countKeywordHits(List<String> expectedKeywords, List<RetrievalEvalHitVO> hits) {
        if (expectedKeywords.isEmpty() || hits.isEmpty()) {
            return 0;
        }
        String evidence = hits.stream()
                .map(hit -> defaultString(hit.getDocumentName()) + " " + defaultString(hit.getSnippetText()))
                .collect(Collectors.joining(" "));
        String normalizedEvidence = normalize(evidence);
        int count = 0;
        for (String keyword : expectedKeywords) {
            if (normalizedEvidence.contains(normalize(keyword))) {
                count++;
            }
        }
        return count;
    }

    private boolean hasExpectedDocument(RetrievalEvalCaseEntity evalCase) {
        return evalCase.getExpectedDocumentId() != null || StringUtils.hasText(evalCase.getExpectedDocumentName());
    }

    private String buildFailureReason(boolean statusPassed, boolean documentPassed, boolean keywordPassed) {
        List<String> reasons = new ArrayList<>();
        if (!statusPassed) {
            reasons.add("status mismatch");
        }
        if (!documentPassed) {
            reasons.add("expected document not found in topK");
        }
        if (!keywordPassed) {
            reasons.add("expected keywords not fully covered");
        }
        return String.join("; ", reasons);
    }

    private KnowledgeBaseEntity ensureKnowledgeBase(Long tenantId, Long knowledgeBaseId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                        .eq(KnowledgeBaseEntity::getId, knowledgeBaseId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge base not found");
        }
        return entity;
    }

    private KnowledgeDocumentEntity resolveExpectedDocument(Long tenantId, Long knowledgeBaseId, Long documentId) {
        if (documentId == null) {
            return null;
        }
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(KnowledgeDocumentEntity::getId, documentId)
                        .last("limit 1")
        );
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Expected document not found");
        }
        return document;
    }

    private String resolveExpectedDocumentName(String requestName, KnowledgeDocumentEntity expectedDocument) {
        if (StringUtils.hasText(requestName)) {
            return requestName;
        }
        return expectedDocument == null ? null : expectedDocument.getDocName();
    }

    private RetrievalEvalCaseEntity getCaseForTenant(Long tenantId, Long id) {
        RetrievalEvalCaseEntity entity = caseMapper.selectOne(
                new LambdaQueryWrapper<RetrievalEvalCaseEntity>()
                        .eq(RetrievalEvalCaseEntity::getTenantId, tenantId)
                        .eq(RetrievalEvalCaseEntity::getId, id)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation case not found");
        }
        return entity;
    }

    private RetrievalEvalRunEntity getRunForTenant(Long tenantId, Long id) {
        RetrievalEvalRunEntity entity = runMapper.selectOne(
                new LambdaQueryWrapper<RetrievalEvalRunEntity>()
                        .eq(RetrievalEvalRunEntity::getTenantId, tenantId)
                        .eq(RetrievalEvalRunEntity::getId, id)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Evaluation run not found");
        }
        return entity;
    }

    private Map<Long, String> loadKnowledgeBaseNames(Long tenantId, List<Long> knowledgeBaseIds) {
        List<Long> ids = knowledgeBaseIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseEntity>()
                                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                                .in(KnowledgeBaseEntity::getId, ids)
                ).stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, KnowledgeBaseEntity::getKbName));
    }

    private RetrievalEvalCaseVO toCaseVO(RetrievalEvalCaseEntity entity, Map<Long, String> knowledgeBaseNames) {
        return RetrievalEvalCaseVO.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .knowledgeBaseName(knowledgeBaseNames.get(entity.getKnowledgeBaseId()))
                .caseName(entity.getCaseName())
                .questionText(entity.getQuestionText())
                .expectedStatus(entity.getExpectedStatus())
                .expectedDocumentId(entity.getExpectedDocumentId())
                .expectedDocumentName(entity.getExpectedDocumentName())
                .expectedKeywords(readKeywords(entity.getExpectedKeywords()))
                .topK(entity.getTopK())
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RetrievalEvalRunVO toRunVO(RetrievalEvalRunEntity entity, boolean includeResults) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        Map<Long, String> knowledgeBaseNames = loadKnowledgeBaseNames(
                tenantId,
                entity.getKnowledgeBaseId() == null ? List.of() : List.of(entity.getKnowledgeBaseId())
        );
        return RetrievalEvalRunVO.builder()
                .id(entity.getId())
                .runNo(entity.getRunNo())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .knowledgeBaseName(knowledgeBaseNames.get(entity.getKnowledgeBaseId()))
                .totalCases(entity.getTotalCases())
                .passedCases(entity.getPassedCases())
                .failedCases(entity.getFailedCases())
                .passRate(toDouble(entity.getPassRate()))
                .recallAtK(toDouble(entity.getRecallAtK()))
                .top1HitRate(toDouble(entity.getTop1HitRate()))
                .noHitAccuracy(toDouble(entity.getNoHitAccuracy()))
                .avgTopScore(toDouble(entity.getAvgTopScore()))
                .avgTopLexicalScore(toDouble(entity.getAvgTopLexicalScore()))
                .avgTopVectorScore(toDouble(entity.getAvgTopVectorScore()))
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdAt(entity.getCreatedAt())
                .results(includeResults ? loadResultVOs(tenantId, entity.getId()) : List.of())
                .build();
    }

    private List<RetrievalEvalResultVO> loadResultVOs(Long tenantId, Long runId) {
        return resultMapper.selectList(
                        new LambdaQueryWrapper<RetrievalEvalResultEntity>()
                                .eq(RetrievalEvalResultEntity::getTenantId, tenantId)
                                .eq(RetrievalEvalResultEntity::getRunId, runId)
                                .orderByAsc(RetrievalEvalResultEntity::getId)
                ).stream()
                .map(this::toResultVO)
                .toList();
    }

    private RetrievalEvalResultVO toResultVO(RetrievalEvalResultEntity entity) {
        return RetrievalEvalResultVO.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .caseId(entity.getCaseId())
                .questionText(entity.getQuestionText())
                .expectedStatus(entity.getExpectedStatus())
                .actualStatus(entity.getActualStatus())
                .expectedDocumentId(entity.getExpectedDocumentId())
                .expectedDocumentName(entity.getExpectedDocumentName())
                .actualTopDocumentId(entity.getActualTopDocumentId())
                .actualTopDocumentName(entity.getActualTopDocumentName())
                .topRecallScore(toDouble(entity.getTopRecallScore()))
                .topLexicalScore(toDouble(entity.getTopLexicalScore()))
                .topVectorScore(toDouble(entity.getTopVectorScore()))
                .hitRank(entity.getHitRank())
                .keywordHitCount(entity.getKeywordHitCount())
                .keywordTotalCount(entity.getKeywordTotalCount())
                .passed(entity.getPassed() != null && entity.getPassed() == 1)
                .failureReason(entity.getFailureReason())
                .hits(readJson(entity.getHitsJson(), HIT_LIST_TYPE))
                .queryVariants(readJson(entity.getQueryVariantJson(), QUERY_VARIANT_LIST_TYPE))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RetrievalEvalHitVO toHitVO(KnowledgeSearchHit hit) {
        return RetrievalEvalHitVO.builder()
                .documentId(hit.getDocumentId())
                .chunkId(hit.getChunkId())
                .documentName(hit.getDocumentName())
                .snippetText(hit.getSnippetText())
                .recallScore(hit.getScore())
                .lexicalScore(hit.getLexicalScore())
                .vectorScore(hit.getVectorScore())
                .recallStrategy(hit.getRecallStrategy())
                .rankNo(hit.getRankNo())
                .build();
    }

    private QueryVariantVO toQueryVariantVO(QueryVariantHit hit) {
        return QueryVariantVO.builder()
                .text(hit.getText())
                .normalizedText(hit.getNormalizedText())
                .source(hit.getSource())
                .weight(hit.getWeight())
                .build();
    }

    private String normalizeExpectedStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : EXPECTED_SUCCESS;
        if (!EXPECTED_SUCCESS.equals(normalized) && !EXPECTED_NO_HIT.equals(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "expectedStatus must be SUCCESS or NO_HIT");
        }
        return normalized;
    }

    private int resolveTopK(Integer topK) {
        int value = topK == null ? defaultTopK : topK;
        return Math.min(20, Math.max(1, value));
    }

    private String writeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> cleaned = keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private List<String> readKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("\\R+")).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        if (!StringUtils.hasText(value)) {
            return emptyList(type);
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            return emptyList(type);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T emptyList(TypeReference<T> ignored) {
        return (T) List.of();
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        List<BigDecimal> effective = values.stream().filter(Objects::nonNull).toList();
        if (effective.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = effective.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(effective.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
