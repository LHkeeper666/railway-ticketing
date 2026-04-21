package com.lhkeeper.ticketing.railway_ticketing;

import java.util.Collections;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.model.ClassAnnotationAttributes;

public class CodeGenerator {

    public static void main(String[] args) {

        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(
                "jdbc:mysql://localhost:3306/railway_ticketing?useSSL=false&serverTimezone=UTC",
                "root",
                "root"
        )

        // 1️⃣ 全局配置
        .globalConfig(builder -> {
            builder.author("jack")
                    .disableOpenDir()
                    // .fileOverride(false)
                    .outputDir(projectPath + "/src/main/java");
        })

        // 2️⃣ 包配置
        .packageConfig(builder -> {
            builder.parent("com.lhkeeper.ticketing.railway_ticketing")
                    .entity("domain.entity")
                    .mapper("mapper")
                    .service("service")
                    .serviceImpl("service.impl")
                    .controller("controller")
                    .pathInfo(Collections.singletonMap(
                            OutputFile.xml,
                            projectPath + "/src/main/resources/mapper"
                    ));
        })

        // 3️⃣ 策略配置（核心）
        .strategyConfig(builder -> {

            // ✅ 不写 addInclude = 所有表
            builder.addTablePrefix("t_", "sys_")
                   .addExclude(
                        "flyway_schema_history",
                        "undo_log"
                   );

            // Entity 策略
            builder.entityBuilder()
                    // .enableLombok()
                    .enableLombok(
                        new ClassAnnotationAttributes("@Data", "lombok.Data"),
                        new ClassAnnotationAttributes("@NoArgsConstructor", "lombok.NoArgsConstructor"),
                        new ClassAnnotationAttributes("@AllArgsConstructor", "lombok.AllArgsConstructor")
                    )
                    // .enableChainModel()            // 链式 set
                    .enableTableFieldAnnotation()
                    .enableFileOverride()
                    .superClass("com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity")
                    .addSuperEntityColumns("id", "create_time", "update_time", "del_flag");

            // Controller 策略
            builder.controllerBuilder()
                    .enableRestStyle();

            // Service 策略
            builder.serviceBuilder()
                    .formatServiceFileName("%sService")
                    .formatServiceImplFileName("%sServiceImpl");
        })

        // 4️⃣ 执行
        .execute();
    }
}
