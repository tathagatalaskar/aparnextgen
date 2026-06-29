package gov.nic.apar.service;

import gov.nic.apar.dto.*;
import gov.nic.apar.entity.*;
import gov.nic.apar.exception.BadRequestException;
import gov.nic.apar.exception.ForbiddenException;
import gov.nic.apar.exception.NotFoundException;
import gov.nic.apar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeDetailsRepository employeeRepository;
    private final UserAccountRepository accountRepository;
    private final EmployeeAcrStatusRepository statusRepository;
    private final AcrCycleRepository cycleRepository;
    private final LookupService lookup;

    private static final int REMINDER_LEAD_DAYS = 14; // start auto-reminders this many days before due

    // ── Employee: their notifications ──
    @Transactional(readOnly = true)
    public List<NotificationView> listFor(Long employeeId) {
        Map<Long, String> names = nameMap();
        return notificationRepository.findByRecipientEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(n -> new NotificationView(
                        n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                        Boolean.TRUE.equals(n.getIsRead()),
                        n.getSenderEmployeeId() != null ? names.get(n.getSenderEmployeeId()) : "System",
                        n.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long employeeId) {
        return notificationRepository.countByRecipientEmployeeIdAndIsReadFalse(employeeId);
    }

    @Transactional
    public void markRead(Long notificationId, Long employeeId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getRecipientEmployeeId().equals(employeeId))
            throw new ForbiddenException("Not your notification");
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(Long employeeId) {
        notificationRepository.findByRecipientEmployeeIdOrderByCreatedAtDesc(employeeId)
                .forEach(n -> { if (!Boolean.TRUE.equals(n.getIsRead())) { n.setIsRead(true); notificationRepository.save(n); } });
    }

    // ── Internal helper used by other services ──
    @Transactional
    public void notify(Long recipientId, Long senderId, String type, String title, String message, String year) {
        notificationRepository.save(Notification.builder()
                .recipientEmployeeId(recipientId)
                .senderEmployeeId(senderId)
                .type(type).title(title).message(message)
                .assessmentYear(year)
                .isRead(false)
                .build());
    }

    // ── Authority: manual notification to one / many / department / all ──
    @Transactional
    public MessageResponse sendManual(SendNotificationRequest req, Long senderId) {
        List<Long> recipients = resolveRecipients(req);
        if (recipients.isEmpty()) throw new BadRequestException("No recipients matched the selection");
        for (Long rid : recipients) {
            notify(rid, senderId, "MANUAL", req.title().trim(), req.message().trim(), null);
        }
        return new MessageResponse("Notification sent to " + recipients.size() + " employee(s).");
    }

    private List<Long> resolveRecipients(SendNotificationRequest req) {
        return switch (req.targetType()) {
            case "ONE", "MANY" -> {
                if (req.employeeIds() == null || req.employeeIds().isEmpty())
                    throw new BadRequestException("Select at least one employee");
                yield req.employeeIds();
            }
            case "DEPARTMENT" -> {
                if (req.departmentId() == null) throw new BadRequestException("Select a department");
                Set<Long> emps = employeeLoginIds();
                yield employeeRepository.findByDepartmentIdAndIsActiveTrue(req.departmentId())
                        .stream().map(EmployeeDetails::getEmployeeId)
                        .filter(emps::contains).toList();
            }
            case "ALL" -> {
                Set<Long> emps = employeeLoginIds();
                yield employeeRepository.findByIsActiveTrue()
                        .stream().map(EmployeeDetails::getEmployeeId)
                        .filter(emps::contains).toList();
            }
            default -> throw new BadRequestException("Invalid target type");
        };
    }

    // ── Authority: send APAR reminders to selected employees (unlocks their form) ──
    @Transactional
    public MessageResponse sendReminders(List<Long> employeeIds, Long senderId) {
        AcrCycle cycle = currentCycle();
        int count = 0;
        for (Long eid : employeeIds) {
            EmployeeDetails emp = employeeRepository.findByEmployeeId(eid).orElse(null);
            if (emp == null) continue;
            notify(eid, senderId, "APAR_REMINDER",
                    "APAR Submission Requested — " + cycle.getAssessmentYear(),
                    "You are requested to fill and submit your APAR for the " + cycle.getAssessmentYear()
                            + " cycle. Due date: " + cycle.getDueDate() + ".",
                    cycle.getAssessmentYear());
            count++;
        }
        return new MessageResponse("APAR reminder sent to " + count + " employee(s).");
    }

    // ── Automatic reminders (scheduled): remind active employees who haven't submitted ──
    @Transactional
    public int generateAutoReminders() {
        Optional<AcrCycle> open = cycleRepository.findFirstByIsOpenTrueOrderByDueDateDesc();
        if (open.isEmpty()) return 0;
        AcrCycle cycle = open.get();
        // only start reminding within the lead window before the due date
        if (LocalDate.now().isBefore(cycle.getDueDate().minusDays(REMINDER_LEAD_DAYS))) return 0;

        Set<Long> withLogin = accountRepository.findAll().stream()
                .filter(a -> a.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(a.getIsActive()))
                .map(UserAccount::getEmployeeId).collect(Collectors.toSet());

        int created = 0;
        for (Long eid : withLogin) {
            boolean submitted = statusRepository
                    .findByEmployeeIdAndAssessmentYear(eid, cycle.getAssessmentYear())
                    .map(s -> Boolean.TRUE.equals(s.getIsAcrSubmitted())).orElse(false);
            if (submitted) continue;
            boolean already = notificationRepository
                    .existsByRecipientEmployeeIdAndTypeAndAssessmentYear(eid, "APAR_REMINDER", cycle.getAssessmentYear());
            if (already) continue;
            notify(eid, null, "APAR_REMINDER",
                    "APAR Due — " + cycle.getAssessmentYear(),
                    "Your APAR for " + cycle.getAssessmentYear() + " is due by " + cycle.getDueDate()
                            + ". Please submit it before the deadline.",
                    cycle.getAssessmentYear());
            created++;
        }
        return created;
    }

    // ── Authority: who hasn't submitted yet ──
    @Transactional(readOnly = true)
    public List<PendingReportView> pendingReports() {
        AcrCycle cycle = currentCycle();
        Map<Long, String> depts = lookup.departments();
        Set<Long> withLogin = accountRepository.findAll().stream()
                .filter(a -> a.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(a.getIsActive()))
                .map(UserAccount::getEmployeeId).collect(Collectors.toSet());

        List<PendingReportView> out = new ArrayList<>();
        for (EmployeeDetails e : employeeRepository.findByIsActiveTrue()) {
            if (!withLogin.contains(e.getEmployeeId())) continue;
            boolean submitted = statusRepository
                    .findByEmployeeIdAndAssessmentYear(e.getEmployeeId(), cycle.getAssessmentYear())
                    .map(s -> Boolean.TRUE.equals(s.getIsAcrSubmitted())).orElse(false);
            if (submitted) continue;
            boolean reminded = notificationRepository
                    .existsByRecipientEmployeeIdAndTypeAndAssessmentYear(e.getEmployeeId(), "APAR_REMINDER", cycle.getAssessmentYear());
            out.add(new PendingReportView(e.getEmployeeId(), e.getFullName(),
                    lookup.nameOr(depts, e.getDepartmentId()), cycle.getAssessmentYear(),
                    cycle.getDueDate(), reminded));
        }
        return out;
    }

    // ── Eligibility: is the APAR form unlocked for this employee? ──
    @Transactional(readOnly = true)
    public SubmitEligibilityView eligibility(Long employeeId) {
        Optional<AcrCycle> open = cycleRepository.findFirstByIsOpenTrueOrderByDueDateDesc();
        if (open.isEmpty())
            return new SubmitEligibilityView(false, null, null, false, false, false);
        AcrCycle cycle = open.get();
        boolean reminded = notificationRepository
                .existsByRecipientEmployeeIdAndTypeAndAssessmentYear(employeeId, "APAR_REMINDER", cycle.getAssessmentYear());
        boolean submitted = statusRepository
                .findByEmployeeIdAndAssessmentYear(employeeId, cycle.getAssessmentYear())
                .map(s -> Boolean.TRUE.equals(s.getIsAcrSubmitted())).orElse(false);
        boolean canSubmit = Boolean.TRUE.equals(cycle.getIsOpen()) && reminded && !submitted;
        return new SubmitEligibilityView(Boolean.TRUE.equals(cycle.getIsOpen()),
                cycle.getAssessmentYear(), cycle.getDueDate(), reminded, submitted, canSubmit);
    }

    public AcrCycle currentCycle() {
        return cycleRepository.findFirstByIsOpenTrueOrderByDueDateDesc()
                .orElseThrow(() -> new BadRequestException("No open assessment cycle configured"));
    }

    /** Employee IDs that have an active EMPLOYEE login (excludes authority + login-less records). */
    private Set<Long> employeeLoginIds() {
        return accountRepository.findAll().stream()
                .filter(a -> a.getRole() == Role.EMPLOYEE && Boolean.TRUE.equals(a.getIsActive()))
                .map(UserAccount::getEmployeeId).collect(Collectors.toSet());
    }

    private Map<Long, String> nameMap() {
        Map<Long, String> m = new HashMap<>();
        employeeRepository.findAll().forEach(e -> m.put(e.getEmployeeId(), e.getFullName()));
        return m;
    }
}
