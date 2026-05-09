package com.knowflow.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.knowledge.entity.KnowledgeChunkIndexEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeChunkIndexMapper extends BaseMapper<KnowledgeChunkIndexEntity> {

    @Delete("DELETE FROM knowledge_chunk_index WHERE document_id = #{documentId}")
    int hardDeleteByDocumentId(@Param("documentId") Long documentId);
}
