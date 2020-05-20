package at.tugraz.asd.LANG.Service;


import at.tugraz.asd.LANG.Exeptions.EditFail;
import at.tugraz.asd.LANG.Exeptions.RatingFail;
import at.tugraz.asd.LANG.Languages;
import at.tugraz.asd.LANG.Messages.in.EditVocabularyMessageIn;
import at.tugraz.asd.LANG.Model.TranslationModel;
import at.tugraz.asd.LANG.Model.VocabularyModel;
import at.tugraz.asd.LANG.Messages.in.CreateVocabularyMessageIn;
import at.tugraz.asd.LANG.Repo.TranslationRepo;
import at.tugraz.asd.LANG.Repo.VocabularyRepo;
import at.tugraz.asd.LANG.Topic;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VocabularyService {

    @Autowired
    VocabularyRepo vocabularyRepo;
    @Autowired
    TranslationRepo translationRepo;

    public VocabularyModel saveVocabulary(CreateVocabularyMessageIn msg){
        Map<Languages, String> translations = msg.getTranslations();
        String vocabulary = msg.getVocabulary();

        List<TranslationModel> translationModels = new ArrayList<>();
        translations.forEach((k,v)->{
            TranslationModel translationModel = new TranslationModel(k, v);
            translationModels.add(translationModel);
            translationRepo.save(translationModel);
        });

        VocabularyModel vocabularyModel = new VocabularyModel(Topic.USER_GENERATED, vocabulary, translationModels, Integer.valueOf(0));
        vocabularyRepo.save(vocabularyModel);
        return vocabularyModel;
    }
    public List<VocabularyModel> getAllVocabulary() {
        return vocabularyRepo.findAll();
    }

    public List<String> getAllVocabularyOfLanguage(Languages language)
    {
        List<String> result = new ArrayList<>();
        List<VocabularyModel> all_vocab = getAllVocabulary();
        all_vocab.forEach(vm->{
            List<TranslationModel> translation_list = vm.getTranslationVocabMapping();
            translation_list.forEach(tl->{
                if (tl.getLanguage() == language)
                    result.add(tl.getVocabulary());
            });
        });
        return result;
    }

    public String getTranslation(Languages language, String word)
    {
        final String[] ret = {null};
        List<VocabularyModel> all_vocab = getAllVocabulary();
        all_vocab.forEach(vm->{
            List<TranslationModel> translation_list = vm.getTranslationVocabMapping();
            translation_list.forEach(tl->{
                if (tl.getVocabulary().equals(word)) {
                    translation_list.forEach(new_tl -> {
                        if (new_tl.getLanguage() == language)
                        {
                            ret[0] = new_tl.getVocabulary();
                        }
                    });
                }
            });
        });
        return ret[0];
    }

    public void editVocabulary(EditVocabularyMessageIn msg) throws EditFail {
        //TODO change so we can edit many times
        //vocabularyRepo.equals(msg.getCurrent_translations());
        AtomicInteger success = new AtomicInteger();
        success.set(0);
        msg.getCurrent_translations().values().forEach(v -> {
            if (!(v.isEmpty())) {
                VocabularyModel toUpdate = vocabularyRepo.findByVocabulary(v);
                if (toUpdate != null) {
                    List<TranslationModel> translationModels_new = new ArrayList<>();
                    msg.getNew_translations().forEach((k, val) -> {
                        TranslationModel translationModel = new TranslationModel(k, val);
                        translationModels_new.add(translationModel);
                        translationRepo.save(translationModel);
                    });
                    toUpdate.getTranslationVocabMapping().clear();
                    toUpdate.setVocabulary(msg.getNew_translations().get(Languages.DE));
                    toUpdate.setTranslationVocabMapping(translationModels_new);
                    toUpdate.setRating(msg.getRating());
                    vocabularyRepo.save(toUpdate);
                    success.set(1);
                }
            }
        });
        if (success.get() == 1) {
            return;
        }
        throw new EditFail();
    }

    public File exportVocabulary() {
        //Get all vocabulary and save in List
        List<VocabularyModel> vocabularies = getAllVocabulary();
        //Create new file to store the content of the List
        File exportFile = new File("backup.csv");
        //Write to the file via BufferedWriter and seperate vie semicolon (catch possible occuring errors)
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportFile), "UTF-8"));
            for (VocabularyModel vocab : vocabularies) {
                StringBuffer singleVocab = new StringBuffer();
                singleVocab.append(vocab.getId());
                singleVocab.append(",");
                singleVocab.append(vocab.getTopic());
                singleVocab.append(",");
                singleVocab.append(vocab.getVocabulary());
                singleVocab.append(",");
                singleVocab.append(vocab.getRating());
                singleVocab.append(",");
                singleVocab.append(vocab.getTranslationVocabMapping());
                bw.write(singleVocab.toString());
                bw.newLine();
            }
            bw.flush();
            bw.close();
        } catch (UnsupportedEncodingException e) {
            System.out.println("UnsupportedEncodingException: " + e);
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException: " + e);
        } catch (IOException e) {
            System.out.println("IOException: " + e);
        }
        //Return the file to the calling methode
        return exportFile;
    }

    public void importVocabulary(MultipartFile backupFile) throws IOException {
        //Create new file to get the absolute path of the MultipartFile of the paramterer
        File importFile = new File(backupFile.getOriginalFilename());
        importFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(importFile);
        fos.write(backupFile.getBytes());
        fos.close();
        String backup_Path = importFile.getAbsolutePath();
        //Read the CSV file and put the content into the repo via foreach (catch possible occuring errors)
        try (
                Reader reader = Files.newBufferedReader(Paths.get(backup_Path));
                CSVReader backupReader = new CSVReader(reader);
        ) {
            List<String[]> vocabularies = backupReader.readAll();
            for (String[] singleVocabulary : vocabularies) {
                Topic topic = Topic.valueOf(singleVocabulary[1]);
                 String vocabulary = singleVocabulary[2];
                 int rating = Integer.parseInt(singleVocabulary[3]);
                String TranslationModel = singleVocabulary[4] + singleVocabulary[5] + singleVocabulary[6] + singleVocabulary[7] + singleVocabulary[8] + singleVocabulary[9] + singleVocabulary[10] + singleVocabulary[11] + singleVocabulary[12];

                List<TranslationModel> translationModels = new ArrayList<>();

                String[] splits = TranslationModel.split("\\bTranslationModel\\b");
                for (String splitStr: splits) {
                    String[] element = splitStr.split(",");
                    System.out.println("Elementlength" + element.length);
                    String lang = element[1].substring(element[1].indexOf('=') + 1);
                    String vocab = element[2].substring(element[2].indexOf('=') + 1);
                    Map<Languages, String> translations = new HashMap<>();
                    translations.put(Languages.valueOf(lang), vocab);
                    translations.forEach((k, v) -> {
                        TranslationModel translationModel = new TranslationModel(k, v);
                        translationModels.add(translationModel);
                        translationRepo.save(translationModel);
                    });
                }

                VocabularyModel vocabularyModel = new VocabularyModel(topic, vocabulary, translationModels, rating);
                vocabularyRepo.save(vocabularyModel);
            }
        } catch (CsvException e) {
            System.out.println("CsvException: " + e);
        }
    }
}
