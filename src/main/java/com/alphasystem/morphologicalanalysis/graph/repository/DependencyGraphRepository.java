package com.alphasystem.morphologicalanalysis.graph.repository;

import com.alphasystem.morphologicalanalysis.graph.model.DependencyGraph;
import com.alphasystem.persistence.mongo.repository.BaseRepository;

import java.util.List;

/**
 * @author sali
 */
public interface DependencyGraphRepository extends BaseRepository<DependencyGraph> {

    /**
     * @param chapterNumber
     * @param verseNumber
     * @return
     */
    List<DependencyGraph> findByChapterNumberAndVerseNumber(Integer chapterNumber, Integer verseNumber);

    /**
     * @param chapterNumber
     * @param verseNumber
     * @param segmentNumber
     * @return
     */
    DependencyGraph findByChapterNumberAndVerseNumberAndSegmentNumber(Integer chapterNumber,
                                                                      Integer verseNumber,
                                                                      Integer segmentNumber);

    /**
     * @param chapterNumber
     * @param verseNumber
     * @return
     */
    Long countByChapterNumberAndVerseNumber(Integer chapterNumber, Integer verseNumber);
}