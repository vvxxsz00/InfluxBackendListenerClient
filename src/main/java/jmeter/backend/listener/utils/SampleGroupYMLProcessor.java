package jmeter.backend.listener.utils;


import org.apache.jmeter.services.FileServer;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the sampler groups data in yml file placed by the testplan.jmx file.
 * e.g.
 * T100_TransactionGroup1:
 *  - T1.*
 * T200_TransactionGroup2:
 *  - T2.*
 * T300_TransactionGroup3:
 *  - T3.*
 */

public class SampleGroupYMLProcessor {

    public static LinkedHashMap<String, String> loadFromFile(String path) throws IOException {
        LinkedHashMap<String, String> reversedMap = new LinkedHashMap<>();
        Map<String, List<String>> map;
        Yaml yaml = new Yaml();
        File f = new File(path);
        f = (f.isAbsolute() || f.exists()) ? f : new File(FileServer.getFileServer().getBaseDir(), path);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                Iterable<Object> itr = yaml.loadAll(in);
                for (Object o : itr) {
                    map = (Map<String, List<String>>) o;
                    for (Map.Entry<String,List<String>> set : map.entrySet()){
                        for (String value : set.getValue()){
                            reversedMap.put(value,set.getKey());
                        }
                    }
                }
            }

        }
        return reversedMap;
    }
}