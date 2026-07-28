package com.pointagepro.leave;

import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class LeaveStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaveStatusScheduler.class);

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final NotificationService notificationService;

    public LeaveStatusScheduler(EmployeeRepository employeeRepository,
                                 LeaveRequestRepository leaveRequestRepository,
                                 NotificationService notificationService) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.notificationService = notificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        restoreExpiredLeaves();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void restoreExpiredLeaves() {
        LocalDate today = LocalDate.now();
        List<Employee> onLeave = employeeRepository.findByStatus("CONGE");

        for (Employee emp : onLeave) {
            long activeLeaves = leaveRequestRepository.countActiveLeavesByEmployee(emp.getId(), today);
            if (activeLeaves == 0) {
                emp.setStatus("ACTIF");
                employeeRepository.save(emp);
                log.info("Employee {} {} restored to ACTIF (leave ended)", emp.getFirstName(), emp.getLastName());
                notificationService.notify("AUTO_RESTORE", "Congé expiré",
                    emp.getFirstName() + " " + emp.getLastName() + " — fin de congé, restauré ACTIF",
                    "LOW", "EMPLOYEE", emp.getId());
            }
        }
    }
}
