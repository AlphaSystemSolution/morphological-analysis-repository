package com.alphasystem.morphologicalanalysis.util;

import com.alphasystem.arabic.model.ArabicWord;
import com.alphasystem.arabic.model.NamedTemplate;
import com.alphasystem.morphologicalanalysis.common.model.VerseTokenPairGroup;
import com.alphasystem.morphologicalanalysis.common.model.VerseTokensPair;
import com.alphasystem.morphologicalanalysis.graph.model.DependencyGraph;
import com.alphasystem.morphologicalanalysis.graph.model.GraphNode;
import com.alphasystem.morphologicalanalysis.graph.model.TerminalNode;
import com.alphasystem.morphologicalanalysis.graph.model.support.GraphNodeType;
import com.alphasystem.morphologicalanalysis.graph.repository.DependencyGraphRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.GraphNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.HiddenNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.ImpliedNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.PartOfSpeechNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.PhraseNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.ReferenceNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.RelationshipNodeRepository;
import com.alphasystem.morphologicalanalysis.graph.repository.TerminalNodeRepository;
import com.alphasystem.morphologicalanalysis.morphology.model.MorphologicalEntry;
import com.alphasystem.morphologicalanalysis.morphology.model.RootLetters;
import com.alphasystem.morphologicalanalysis.morphology.repository.DictionaryNotesRepository;
import com.alphasystem.morphologicalanalysis.morphology.repository.MorphologicalEntryRepository;
import com.alphasystem.morphologicalanalysis.wordbyword.model.Chapter;
import com.alphasystem.morphologicalanalysis.wordbyword.model.Location;
import com.alphasystem.morphologicalanalysis.wordbyword.model.QChapter;
import com.alphasystem.morphologicalanalysis.wordbyword.model.QToken;
import com.alphasystem.morphologicalanalysis.wordbyword.model.QVerse;
import com.alphasystem.morphologicalanalysis.wordbyword.model.Token;
import com.alphasystem.morphologicalanalysis.wordbyword.model.Verse;
import com.alphasystem.morphologicalanalysis.wordbyword.model.support.WordType;
import com.alphasystem.morphologicalanalysis.wordbyword.repository.ChapterRepository;
import com.alphasystem.morphologicalanalysis.wordbyword.repository.LocationRepository;
import com.alphasystem.morphologicalanalysis.wordbyword.repository.TokenRepository;
import com.alphasystem.morphologicalanalysis.wordbyword.repository.VerseRepository;
import com.alphasystem.morphologicalanalysis.wordbyword.util.ChapterComparator;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.sort;

/**
 * @author sali
 */
@Component
public class MorphologicalAnalysisRepositoryUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MorphologicalAnalysisRepositoryUtil.class);

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private VerseRepository verseRepository;
    @Autowired private TokenRepository tokenRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private DependencyGraphRepository dependencyGraphRepository;
    @Autowired private TerminalNodeRepository terminalNodeRepository;
    @Autowired private ImpliedNodeRepository impliedNodeRepository;
    @Autowired private HiddenNodeRepository hiddenNodeRepository;
    @Autowired private ReferenceNodeRepository referenceNodeRepository;
    @Autowired private PartOfSpeechNodeRepository partOfSpeechNodeRepository;
    @Autowired private PhraseNodeRepository phraseNodeRepository;
    @Autowired private RelationshipNodeRepository relationshipNodeRepository;
    @Autowired private MorphologicalEntryRepository morphologicalEntryRepository;
    @Autowired private DictionaryNotesRepository dictionaryNotesRepository;
    private Query findAllChaptersQuery;

    public MorphologicalAnalysisRepositoryUtil() {
        findAllChaptersQuery = new Query();
        findAllChaptersQuery.fields().include("chapterNumber").include("verseCount").include("chapterName");
    }

    private static Token getToken(Integer chapterNumber, Integer verseNumber, Integer tokenNumber, boolean next,
                                  TokenRepository tokenRepository, MorphologicalAnalysisRepositoryUtil repositoryUtil) {
        LOGGER.debug("Getting request to find token {}:{}:{}", chapterNumber, verseNumber, tokenNumber);
        if (chapterNumber <= 0 || chapterNumber > 114) {
            // no next/previous token
            LOGGER.warn("No token found {}:{}:{}", chapterNumber, verseNumber, tokenNumber);
            return null;
        }
        if (verseNumber == -1) {
            // verse number "-1" indicates that this could be the last verse of this chapter and we don't know how
            // many verses are in this chapter, let's find out now
            verseNumber = repositoryUtil.getVerseCount(chapterNumber);
        }
        if (tokenNumber == -1) {
            // token number "-1" indicates that this could be the last token of this verse and we don't know how
            // many tokens are in this verse, let's find out now
            tokenNumber = repositoryUtil.getTokenCount(chapterNumber, verseNumber);
        }
        // at this stage if both verseNumber and tokenNumber are null, stop now
        if (verseNumber == -1 && tokenNumber == -1) {
            LOGGER.warn("No token found {}:{}:{}", chapterNumber, verseNumber, tokenNumber);
            return null;
        }
        Token dummy = new Token(chapterNumber, verseNumber, tokenNumber, "");
        LOGGER.debug("Finding token {}", dummy.getDisplayName());
        Token token = tokenRepository.findByDisplayName(dummy.getDisplayName());
        if (token == null) {
            if (next) {
                if (tokenNumber > 1) {
                    // we have situation where token number is greater then 0 and we still haven't found our token.
                    // The reference token should have been the last token of the verse, we have two possible cases:
                    // case 1: the reference token might have been the last token of the last verse of the chapter,
                    // in this case we need to go to the first token of first verse of next chapter
                    // case 2: the reference token might have been the last token of any verse other then last verse,
                    // in this case we need to go to the first token of the next verse while staying in the same chapter
                    // we are going to handle case 2 now
                    return getToken(chapterNumber, verseNumber + 1, 1, true, tokenRepository, repositoryUtil);
                } else if (verseNumber > 1) {
                    // handle case 1
                    return getToken(chapterNumber + 1, 1, 1, true, tokenRepository, repositoryUtil);
                }
            } else {
                if (verseNumber == 0) {
                    // handle case 3
                    return getToken(chapterNumber - 1, -1, -1, false, tokenRepository, repositoryUtil);
                } else if (tokenNumber == 0) {
                    // the reference token should have been the first token of verse, now there are two possible cases:
                    // case 3: the reference token might have been the first token of the first verse of the chapter,
                    // in this case we need to go to the last token of last verse of previous chapter
                    // case 4: the reference token might have been the first token of any verse other then first verse,
                    // in this case we need to go to the last token of the previous verse while staying in the same chapter
                    // we are going to handle case 4 now
                    // but we don't know the how many tokens in the previous verse, we are going to pass -1 as the token
                    // number
                    return getToken(chapterNumber, verseNumber - 1, -1, false, tokenRepository, repositoryUtil);
                }
            }
        }
        return token;
    }

    // Business methods

    /**
     * @return all chapters
     */
    public List<Chapter> findAllChapters() {
        List<Chapter> chapters = mongoTemplate.find(findAllChaptersQuery,
                Chapter.class, Chapter.class.getSimpleName().toLowerCase());
        sort(chapters, new ChapterComparator());
        return chapters;
    }

    public void mergeTokens(int chapterNumber, int verseNumber, int... tokenNumbers) {
        if (ArrayUtils.isEmpty(tokenNumbers) || tokenNumbers.length <= 1) {
            return;
        }
        LOGGER.info("Merging tokens \"{}\" in chapter \"{}\" and verse \"{}\".", ArrayUtils.toString(tokenNumbers),
                chapterNumber, verseNumber);
        final List<Token> tokens = tokenRepository.findByChapterNumberAndVerseNumber(chapterNumber, verseNumber);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        LOGGER.info("Total number of tokens: \"{}\".", tokens.size());
        removeCurrent(tokens);
        createNewTokens(chapterNumber, verseNumber, tokens, tokenNumbers);
    }

    private void removeCurrent(final List<Token> tokens) {
        tokens.forEach(token -> {
            LOGGER.info("Current token: \"{}:{}\" with token text \"{}\"", token, token.getId(), token.tokenWord().toBuckWalter());
            final List<Location> locations = token.getLocations();
            locations.forEach(location -> {
                LOGGER.info("    Current location: \"{}:{}\"", location, location.getId());
                MorphologicalEntry morphologicalEntry = location.getMorphologicalEntry();
                if (morphologicalEntry != null) {
                    LOGGER.info("        MorphologicalEntry \"{}\" is not null for location \"{}:{}\".", morphologicalEntry, location, location.getId());
                    final MorphologicalEntry entry = morphologicalEntryRepository.findOne(morphologicalEntry.getId());
                    final Iterator<Location> iterator = entry.getLocations().iterator();
                    while (iterator.hasNext()) {
                        final Location location1 = iterator.next();
                        if (location1.equals(location)) {
                            LOGGER.info("        Removing location \"{}\" from morphological entry \"{}\"", location1.getId(), morphologicalEntry.getId());
                            iterator.remove();
                        }
                    }
                    morphologicalEntryRepository.save(entry);
                    location.setMorphologicalEntry(null);
                }

                // delete location
                locationRepository.delete(location);
            }); // end of location forEach
            // delete token
            tokenRepository.delete(token);
        }); // end of token forEach
    }

    private void createNewTokens(int chapterNumber, int verseNumber, List<Token> tokens, int... tokenNumbers) {
        List<Token> newTokens = new ArrayList<>();
        int firstTokenNumber = tokenNumbers[0];
        int tokenNumber = 1;
        int index = 0;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            String tokenText = token.getToken();
            if (firstTokenNumber == token.getTokenNumber()) {
                for (int i = 1; i < tokenNumbers.length; i++) {
                    index++;
                    token = tokens.get(index);
                    tokenText += " " + token.getToken();
                }
            }
            Token newToken = new Token(chapterNumber, verseNumber, tokenNumber, tokenText);
            Location newLocation = new Location(chapterNumber, verseNumber, tokenNumber, 1, WordType.NOUN);
            newToken.addLocation(newLocation);
            newTokens.add(newToken);
            locationRepository.save(newLocation);
            tokenRepository.save(newToken);
            LOGGER.info("NEW \"{}:{}:{}\", \"{}:{}\"", newToken, newToken.getId(), newToken.tokenWord().toBuckWalter(), newLocation, newLocation.getId());
            tokenNumber++;
            index++;
        }

        final Verse verse = verseRepository.findByChapterNumberAndVerseNumber(chapterNumber, verseNumber);
        verse.setTokens(newTokens);
        verse.setTokenCount(newTokens.size());
        verseRepository.save(verse);
    }

    public int getTokenCount(Integer chapterNumber, Integer verseNumber) {
        QVerse qVerse = QVerse.verse1;
        BooleanExpression predicate = qVerse.chapterNumber.eq(chapterNumber).and(qVerse.verseNumber.eq(verseNumber));
        Verse verse = verseRepository.findOne(predicate);
        return (verse == null) ? 0 : verse.getTokenCount();
    }

    public int getVerseCount(Integer chapterNumber) {
        BooleanExpression predicate = QChapter.chapter.chapterNumber.eq(chapterNumber);
        Chapter chapter = chapterRepository.findOne(predicate);
        return (chapter == null) ? 0 : chapter.getVerseCount();
    }

    public Token getNextToken(Token token) {
        LOGGER.debug("Getting next token for {}", token);
        if (token == null) {
            return null;
        }
        Token result = getToken(token.getChapterNumber(), token.getVerseNumber(), token.getTokenNumber() + 1, true,
                tokenRepository, this);
        LOGGER.debug("Next token for {} is {}", token, result);
        return result;
    }

    public Token getPreviousToken(Token token) {
        LOGGER.debug("Getting previous token for {}", token);
        if (token == null) {
            return null;
        }
        Token result = getToken(token.getChapterNumber(), token.getVerseNumber(), token.getTokenNumber() - 1, false,
                tokenRepository, this);
        LOGGER.debug("Previous token for {} is {}", token, result);
        return result;
    }

    public List<Token> getTokens(VerseTokenPairGroup group) {
        List<VerseTokensPair> pairs = group.getPairs();
        if (pairs == null || pairs.isEmpty()) {
            return new ArrayList<>();
        }
        VerseTokensPair pair = pairs.get(0);
        QToken qToken = QToken.token1;
        BooleanExpression predicate = qToken.verseNumber.eq(pair.getVerseNumber()).
                and(qToken.tokenNumber.between(pair.getFirstTokenIndex(), pair.getLastTokenIndex()));
        for (int i = 1; i < pairs.size(); i++) {
            pair = pairs.get(i);
            BooleanExpression predicate1 = qToken.verseNumber.eq(pair.getVerseNumber())
                    .and(qToken.tokenNumber.between(pair.getFirstTokenIndex(), pair.getLastTokenIndex()));
            predicate = predicate.or(predicate1);
        }
        predicate = qToken.chapterNumber.eq(group.getChapterNumber()).and(predicate);
        if (group.isIncludeHidden()) {
            predicate = predicate.and(qToken.hidden.eq(true));
        }
        LOGGER.info(format("Query for \"getTokens\" is {%s}", predicate));
        return (List<Token>) tokenRepository.findAll(predicate);
    }

    public ArabicWord getLocationWord(Location location) {
        if (location == null || location.isTransient()) {
            return null;
        }
        ArabicWord locationWord = null;

        Token token = tokenRepository.findByChapterNumberAndVerseNumberAndTokenNumber(location.getChapterNumber(),
                location.getVerseNumber(), location.getTokenNumber());
        if (token != null) {
            locationWord = ArabicWord.getSubWord(token.tokenWord(), location.getStartIndex(), location.getEndIndex());
        }

        return locationWord;
    }

    public List<DependencyGraph> getDependencyGraphs(VerseTokenPairGroup group) {
        List<VerseTokensPair> pairs = group.getPairs();
        if (pairs == null || pairs.isEmpty()) {
            return new ArrayList<>();
        }

        LOGGER.info(format("Group to find DependencyGraph is {%s}", group));
        int index = 0;
        VerseTokensPair pair = pairs.get(index);
        Criteria[] verseNumberCriterion = new Criteria[pairs.size()];
        verseNumberCriterion[index] = Criteria.where("verseNumber").is(pair.getVerseNumber());
        for (index = 1; index < pairs.size(); index++) {
            pair = pairs.get(index);
            verseNumberCriterion[index] = Criteria.where("verseNumber").is(pair.getVerseNumber());
        }
        Criteria tokensCriteria = Criteria.where("tokens").elemMatch(new Criteria().orOperator(verseNumberCriterion));

        Query query = new Query();
        query.addCriteria(Criteria.where("chapterNumber").is(group.getChapterNumber())).addCriteria(tokensCriteria)
                .with(new Sort("tokens.verseNumber"));

        LOGGER.info(format("Query for \"getDependencyGraphs\" is {%s}", query));
        return mongoTemplate.find(query, DependencyGraph.class);
    }

    public void saveDependencyGraph(DependencyGraph dependencyGraph, List<Token> impliedOrHiddenTokens,
                                    Map<GraphNodeType, List<String>> removalIds) {
        if (impliedOrHiddenTokens != null && !impliedOrHiddenTokens.isEmpty()) {
            impliedOrHiddenTokens.forEach(tokenRepository::save);
        }
        dependencyGraphRepository.save(dependencyGraph);
        if (removalIds != null && !removalIds.isEmpty()) {
            removalIds.entrySet().forEach(this::removeNode);
        }
    }

    public void deleteDependencyGraph(String id, Map<GraphNodeType, List<String>> removalIds) {
        if (!removalIds.isEmpty()) {
            removalIds.entrySet().forEach(this::removeNode);
        }
        DependencyGraph dependencyGraph = dependencyGraphRepository.findOne(id);
        VerseTokenPairGroup group = new VerseTokenPairGroup();
        group.setIncludeHidden(true);
        group.setChapterNumber(dependencyGraph.getChapterNumber());
        group.getPairs().addAll(dependencyGraph.getTokens());
        List<Token> hiddenTokens = getTokens(group);
        if (hiddenTokens != null && !hiddenTokens.isEmpty()) {
            hiddenTokens.forEach(token -> locationRepository.delete(token.getLocations()));
            tokenRepository.delete(hiddenTokens);
        }
        dependencyGraphRepository.delete(id);
    }

    @SuppressWarnings({"unchecked"})
    private void removeNode(Map.Entry<GraphNodeType, List<String>> entry) {
        GraphNodeType key = entry.getKey();
        List<String> ids = entry.getValue();
        GraphNodeRepository repository = getRepository(key);
        ids.forEach(repository::delete);
    }

    public DependencyGraph getDependencyGraph(String displayName) {
        return dependencyGraphRepository.findByDisplayName(displayName);
    }

    public MorphologicalEntry findMorphologicalEntry(MorphologicalEntry src) {
        src.initDisplayName();
        return morphologicalEntryRepository.findByDisplayName(src.getDisplayName());
    }

    public MorphologicalEntry findMorphologicalEntry(RootLetters src, NamedTemplate form) {
        return findMorphologicalEntry(new MorphologicalEntry(src, form));
    }


    @SuppressWarnings("unchecked")
    public <N extends GraphNode, R extends GraphNodeRepository<N>> R getRepository(GraphNodeType nodeType) {
        R repository = null;
        switch (nodeType) {
            case TERMINAL:
                repository = (R) terminalNodeRepository;
                break;
            case PART_OF_SPEECH:
                repository = (R) partOfSpeechNodeRepository;
                break;
            case PHRASE:
                repository = (R) phraseNodeRepository;
                break;
            case RELATIONSHIP:
                repository = (R) relationshipNodeRepository;
                break;
            case REFERENCE:
                repository = (R) referenceNodeRepository;
                break;
            case HIDDEN:
                repository = (R) hiddenNodeRepository;
                break;
            case IMPLIED:
                repository = (R) impliedNodeRepository;
                break;
            case ROOT:
                break;
        }
        return repository;
    }

    @SuppressWarnings({"unchecked"})
    public void delete(GraphNode graphNode) {
        if (graphNode == null) {
            return;
        }
        GraphNodeType graphNodeType = graphNode.getGraphNodeType();
        switch (graphNodeType) {
            case TERMINAL:
            case IMPLIED:
            case REFERENCE:
            case HIDDEN:
                TerminalNode tn = (TerminalNode) graphNode;
                tn.getPartOfSpeechNodes().forEach(partOfSpeechNode -> {
                    if (partOfSpeechNode != null) {
                        partOfSpeechNodeRepository.delete(partOfSpeechNode.getId());
                    }
                });
                break;
        }
        GraphNodeRepository repository = getRepository(graphNodeType);
        repository.delete(graphNode.getId());
    }

}
