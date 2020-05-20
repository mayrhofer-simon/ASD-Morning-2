package at.tugraz.asd.LANG.Controller;


import at.tugraz.asd.LANG.Exeptions.EditFail;
import at.tugraz.asd.LANG.Exeptions.RatingFail;
import at.tugraz.asd.LANG.Languages;
import at.tugraz.asd.LANG.Messages.in.CreateVocabularyMessageIn;
import at.tugraz.asd.LANG.Messages.in.EditVocabularyMessageIn;
import at.tugraz.asd.LANG.Messages.in.RatingVocabularyMessageIn;
import at.tugraz.asd.LANG.Messages.out.TranslationOut;
import at.tugraz.asd.LANG.Messages.out.VocabularyLanguageOut;
import at.tugraz.asd.LANG.Messages.out.VocabularyOut;
import at.tugraz.asd.LANG.Model.VocabularyModel;
import at.tugraz.asd.LANG.Service.VocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {

    @Autowired
    VocabularyService service;

    @PostMapping
    public ResponseEntity addVocabulary(@RequestBody CreateVocabularyMessageIn msg){
        service.saveVocabulary(msg);
        return ResponseEntity.ok(null);
    }

    @GetMapping
    @ResponseBody
    public ResponseEntity getAllVocabulary(){
        ArrayList<VocabularyOut> ret = new ArrayList<>();
        List<VocabularyModel> vocab = service.getAllVocabulary();
        if(vocab.isEmpty())
            return ResponseEntity.noContent().build();
        vocab.forEach(el->{
            HashMap<Languages, String> translation = new HashMap<>();
            el.getTranslationVocabMapping().forEach(translationModel -> {
                translation.put(translationModel.getLanguage(), translationModel.getVocabulary());
            });
            ret.add(new VocabularyOut(
                    el.getTopic(),
                    el.getVocabulary(),
                    translation,
                    el.getRating()
            ));
        });
        return ResponseEntity.ok(ret);
    }

    @GetMapping (path = "{Language}")
    public ResponseEntity getAllVocabularyOfLanguage(@PathVariable("Language") Languages language)
    {
        VocabularyLanguageOut ret = new VocabularyLanguageOut(service.getAllVocabularyOfLanguage(language));
        return ResponseEntity.ok(ret);
    }

    @GetMapping (path = "{Language}/{word}")
    public ResponseEntity getTranslation(@PathVariable("Language") Languages language, @PathVariable("word") String word)
    {
        TranslationOut ret = new TranslationOut(service.getTranslation(language, word));
        return ResponseEntity.ok(ret);
    }
    @PutMapping
    @ResponseBody
    public ResponseEntity editVocabulary(@RequestBody EditVocabularyMessageIn msg){
        try{
            service.editVocabulary(msg);
            return ResponseEntity.ok(null);
        }
        catch (EditFail e){
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping (path = "alphabetically/{aORz}")
    @ResponseBody
    public ResponseEntity getAllVocabularyAlphabetically1(@PathVariable("aORz")String aOrz){
        ArrayList<VocabularyOut> ret = new ArrayList<>();
        List<VocabularyModel> vocab = service.getAllVocabulary();

        if(vocab.isEmpty())
            return ResponseEntity.noContent().build();
        if(aOrz.equals("a"))
        {
            vocab.sort(new Comparator<VocabularyModel>() {
                @Override
                public int compare(VocabularyModel vocabularyModel, VocabularyModel t1) {
                    return vocabularyModel.getVocabulary().compareTo(t1.getVocabulary());
                }
            });
        }
        if(aOrz.equals("z"))
        {
            vocab.sort(new Comparator<VocabularyModel>() {
                @Override
                public int compare(VocabularyModel vocabularyModel, VocabularyModel t1) {
                    return t1.getVocabulary().compareTo(vocabularyModel.getVocabulary());
                }
            });
        }
        vocab.forEach(el->{
            HashMap<Languages, String> translation = new HashMap<>();
            el.getTranslationVocabMapping().forEach(translationModel -> {
                translation.put(translationModel.getLanguage(), translationModel.getVocabulary());
            });
            ret.add(new VocabularyOut(
                    el.getTopic(),
                    el.getVocabulary(),
                    translation,
                    el.getRating()
            ));
        });
        return ResponseEntity.ok(ret);
    }

    @GetMapping (path = "random")
    @ResponseBody
    public ResponseEntity getRandomVocabulary() {
        int testSize = 10;      // change if test size varies in future issues
        ArrayList<VocabularyOut> ret = new ArrayList<>();
        List<VocabularyModel> randomVocab = new ArrayList<>();
        Random rand = new Random();

        List<VocabularyModel> vocab = service.getAllVocabulary();

        // Check if vocab list exists and has enough vocabs
        if(vocab.isEmpty() || vocab.size() < testSize)
            return ResponseEntity.noContent().build();

        // Select x amount of random vocabs from vocab list, and remove element from vocab list to avoid duplicates
        for (int i = 0; i < testSize; i++) {
            VocabularyModel randomVocabItem = vocab.get(rand.nextInt(vocab.size()));
            randomVocab.add(randomVocabItem);
            vocab.remove(randomVocabItem);
        }
        System.out.println("Random Vocabs are: " + randomVocab.toString());

        // Build response
        randomVocab.forEach(el->{
            HashMap<Languages, String> translation = new HashMap<>();
            el.getTranslationVocabMapping().forEach(
                    translationModel -> translation.put(translationModel.getLanguage(), translationModel.getVocabulary())
            );
            ret.add(new VocabularyOut(
                    el.getTopic(),
                    el.getVocabulary(),
                    translation,
                    el.getRating()

            ));
        });

        return ResponseEntity.ok(ret);
    }

    @GetMapping  (path = "Export")
    public ResponseEntity exportBackup(){
        try{
            File backup = service.exportVocabulary();

            Path path = Paths.get(backup.getAbsolutePath());
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(path));

            return ResponseEntity.ok()
                    .contentLength(backup.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }
        catch (Exception e){
            System.out.println("Fehler beim Exportieren");
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping (path = "Import")
    public ResponseEntity importBackup(@RequestParam("file") MultipartFile Backup_File){
        try{
            service.importVocabulary(Backup_File);
            return ResponseEntity.ok(null);
        }
        catch (Exception e){
            System.out.println("Error Importing File" + e);
            return ResponseEntity.badRequest().body(null);
        }
    }
}
