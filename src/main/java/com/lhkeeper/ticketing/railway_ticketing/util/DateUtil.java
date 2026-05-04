package com.lhkeeper.ticketing.railway_ticketing.util;

import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class DateUtil {

    public static boolean validateFormat(Date date, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);  // 禁止宽松解析
        try {
            sdf.parse(date.toString());
            return true;  // 如果解析成功，则格式正确
        } catch (ParseException e) {
            return false;  // 如果解析失败，则格式不正确
        }
    }

    public static boolean beforeToday(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now());
    }
}
