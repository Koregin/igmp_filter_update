package ru.koregin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.koregin.model.Abonent;
import ru.koregin.repo.SqlRepository;
import ru.koregin.utils.TelnetExecutor;

import java.util.ArrayList;
import java.util.List;

public class MainExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SqlRepository.class.getName());

    public static void main(String[] args) {
        long timeExec = System.currentTimeMillis();
        TelnetExecutor telnetExecutor = new TelnetExecutor();
        try (SqlRepository store = new SqlRepository()) {
            store.init();
            List<Abonent> abonents = store.findAllOperAbonents();
            for (Abonent abonent : abonents) {
                List<String> ipGroups = new ArrayList<>();
                int userId = abonent.getUserId();
                int port = abonent.getPort();
                String switchIp = abonent.getSwitchIp();
                String userName = abonent.getUserName();
                LOG.info("\n---------- Обработка абонента: ID=" + userId + " - " + userName + " ----------");
                if (store.checkBlockAbonent(userId) && store.checkBalance(userId) && store.checkWriteOffsCurrentMonth(userId)) {
                    LOG.info("Абонент ID: " + userId + " прошел проверку. Генерируем igmp профиль");
                    ipGroups = store.getIpGroupsForAbonent(userId);
                } else {
                    LOG.info("Абонент ID: " + userId + " не прошел проверку. Генерируем профиль по умолчанию");
                }
                if (telnetExecutor.igmpProfileUpdate(userId, switchIp, port, ipGroups)) {
                    store.setCiscoJob(userId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOG.info("Exec time: " + (System.currentTimeMillis() - timeExec) + " ms.");
    }
}
