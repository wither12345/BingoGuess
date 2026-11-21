package dev.guessbingo.lex;

import dev.guessbingo.GuessBingo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class LexiconReader extends SimpleJsonResourceReloadListener<Question>{
    private static final HashMap<Integer, ClassifiedQuestions> questions = new HashMap<>();
    private static final EnumMap<Type, HashMap<Identifier, Question>> questionMap = new EnumMap<>(Type.class);

    private static final FileToIdConverter ASSET_LISTER = FileToIdConverter.json("lexicon");

    public LexiconReader() {
        super(Question.CODEC, ASSET_LISTER);
    }

    public static Question get(RawQuestion question) {
        Question picked = questionMap.get(question.type()).get(question.id());
        if(picked == null){
            for(Map.Entry<Type, HashMap<Identifier, Question>> entry : questionMap.entrySet()){
                picked = entry.getValue().get(question.id());
                if(picked != null) return picked;
            }
        }
        return picked;
    }

    @Override
    protected void apply(@NonNull Map<Identifier, Question> preparations, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        questions.clear();
        questionMap.clear();
        preparations.forEach((_, question) -> {
            questions.computeIfAbsent(question.difficulty(), _ -> new ClassifiedQuestions()).push(question);
            questionMap.computeIfAbsent(question.type(), _ -> new HashMap<>()).put(question.id(), question);
        });
        GuessBingo.LOGGER.info("Loaded {} types of questions!", questions.size());
    }

    public static HashMap<Integer, ClassifiedQuestions> getQuestions(){
        return questions;
    }

    public static Question getRandom(int diff){
        Type type = randType();
        return questions.get(diff).getRandomQuestion(type);
    }

    private static Type randType(){
        int rand = RandomSource.create().nextInt(4);
        return switch (rand){
            case 0 -> Type.ITEM;
            case 1 -> Type.BLOCK;
            case 2 -> Type.ENTITY_TYPE;
            case 3 -> Type.ADVANCEMENT;
            default -> throw new IllegalArgumentException("Invalid random number: " + rand);
        };
    }

    public static class ClassifiedQuestions {
        private final Map<Type, List<Question>> questions = new HashMap<>();

        public void push(Question question){
            questions.computeIfAbsent(question.type(), _ -> new ArrayList<>()).add(question);
        }

        public Question getRandomQuestion(Type type){
            List<Question> picked = questions.getOrDefault(type, new ArrayList<>());
            if(picked.isEmpty()) return null;
            return picked.get(RandomSource.create().nextInt(picked.size()));
        }

        public List<Question> all(){
            List<Question> all = new ArrayList<>();
            questions.values().forEach(all::addAll);
            return all;
        }
    }
}