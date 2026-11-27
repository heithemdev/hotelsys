package com.hotelsys.app;

import com.hotelsys.repository.Db;
import com.hotelsys.service.DemoData;
import java.nio.file.Path;

public final class Launcher {
    private Launcher() { }

    static Db database() {
        return new Db(Path.of(System.getProperty("hotelsys.dataDir", "data"), "hotelsys.db"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--seed-demo")) {
            DemoData.seed(database());
            return;
        }
        javafx.application.Application.launch(HotelApp.class, args);
    }
}
