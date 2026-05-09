package com.knowflow.integration.search.local;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryVariantExpanderTests {

    private final QueryVariantExpander expander = new QueryVariantExpander();

    @Test
    void shouldExpandBilingualTechnicalTerms() {
        List<QueryVariantExpander.QueryVariant> variants = expander.expand("大模型知识库怎么转人工");

        assertThat(variants)
                .extracting(QueryVariantExpander.QueryVariant::text)
                .anySatisfy(text -> assertThat(text).contains("large model"))
                .anySatisfy(text -> assertThat(text).contains("知识库"))
                .anySatisfy(text -> assertThat(text).contains("转人工"));
    }

    @Test
    void shouldKeepOriginalVariantFirst() {
        List<QueryVariantExpander.QueryVariant> variants = expander.expand("How does ticket handoff work?");

        assertThat(variants).isNotEmpty();
        assertThat(variants.get(0).source()).isEqualTo("ORIGINAL");
        assertThat(variants.get(0).text()).isEqualTo("How does ticket handoff work?");
    }
}
