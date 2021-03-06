package com.alphasystem.morphologicalanalysis.graph.repository;

import com.alphasystem.morphologicalanalysis.graph.model.PartOfSpeechNode;

import java.util.List;

/**
 * @author sali
 */
public interface PartOfSpeechNodeRepository extends GraphNodeRepository<PartOfSpeechNode> {

    /**
     * @param chapterNumber
     * @param verseNumber
     * @param tokenNumber
     * @param locationNumber
     * @return
     */
    Long countByChapterNumberAndVerseNumberAndTokenNumberAndLocationNumber(Integer chapterNumber, Integer verseNumber,
                                                                           Integer tokenNumber, Integer locationNumber);

    List<PartOfSpeechNode> findByChapterNumberAndVerseNumberAndTokenNumberAndLocationNumber(Integer chapterNumber, Integer verseNumber,
                                                                                            Integer tokenNumber, Integer locationNumber);
}
