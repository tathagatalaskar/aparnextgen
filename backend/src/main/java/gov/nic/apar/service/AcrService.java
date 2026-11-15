package gov.nic.apar.service;

import gov.nic.apar.dto.*;
import gov.nic.apar.entity.*;
import gov.nic.apar.exception.ForbiddenException;
import gov.nic.apar.exception.BadRequestException;
import gov.nic.apar.exception.NotFoundException;
import gov.nic.apar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AcrService {

    private final EmployeeDetailsRepository employeeRepository;
    private final EmployeeAcrStatusRepository statusRepository;
    private final EmployeeAcrDetailRepository detailRepository;
    private final AcrMapper mapper;
    private final LookupService lookup;
    private final NotificationService notificationService;

    // ── List ACRs (role-based) ──
    @Transactional(readOnly = true)
    public List<AcrSummaryView> list(UserAccount user) {
        Map<Long, String> depts = lookup.departments();
        List<EmployeeAcrStatus> rows = isAuthority(user)
                ? statusRepository.findAllByOrderBySubmittedAtDesc()
                : statusRepository.findByEmployeeIdOrderByAssessmentYearDesc(user.getEmployeeId());
        return rows.stream().map(s -> mapper.toSummary(s, depts)).toList();
    }

    @Transactional(readOnly = true)
    public AcrDetailView getDetail(Long statusId, UserAccount user) {
        EmployeeAcrStatus s = loadStatus(statusId);
        if (!isAuthority(user) && !s.getEmployeeId().equals(user.getEmployeeId()))
            throw new ForbiddenException("You can only view your own ACR");
        return mapper.toDetail(s);
    }

    // ── Employee submits ACR — ONLY if an APAR reminder has unlocked it ──
    @Transactional
    public AcrDetailView submit(SubmitAcrRequest req, UserAccount user) {
        if (isAuthority(user))
            throw new ForbiddenException("Authority cannot submit an ACR");

        Long empId = user.getEmployeeId();

        // Gate: the APAR form is only available after the authority sends a reminder
        SubmitEligibilityView elig = notificationService.eligibility(empId);
        if (!elig.cycleOpen())
            throw new ForbiddenException("The assessment cycle is closed");
        if (!elig.reminded())
            throw new ForbiddenException("You have not been asked to submit your APAR yet. Please wait for the reporting authority's reminder.");
        if (elig.alreadySubmitted())
            throw new ForbiddenException("You have already submitted your APAR for this cycle");

        String year = elig.assessmentYear();
        if (!year.equals(req.assessmentYear())) {
            throw new BadRequestException("The APAR cycle has changed. Refresh the page and submit again.");
        }
        EmployeeDetails emp = employeeRepository.findByEmployeeId(empId)
                .orElseThrow(() -> new NotFoundException("Employee record not found"));

        EmployeeAcrStatus status = statusRepository
                .findByEmployeeIdAndAssessmentYear(empId, year)
                .orElseGet(EmployeeAcrStatus::new);

        status.setEmployeeId(empId);
        status.setOrganizationId(emp.getOrganizationId());
        status.setOfficeId(emp.getOfficeId());
        status.setDepartmentId(emp.getDepartmentId());
        status.setEmpIniName(emp.getEmpIniName());
        status.setEmpFirstName(emp.getEmpFirstName());
        status.setEmpMidName(emp.getEmpMidName());
        status.setEmpLastName(emp.getEmpLastName());
        status.setAssessmentYear(year);
        status.setIsAcrSubmitted(true);
        status.setSubmittedAt(OffsetDateTime.now());
        statusRepository.save(status);

        detailRepository.deleteByEmployeeIdAndAssessmentYear(empId, year);
        short seq = 1;
        for (AcrTargetDTO t : req.targets()) {
            detailRepository.save(EmployeeAcrDetail.builder()
                    .employeeId(empId)
                    .organisationId(emp.getOrganizationId())
                    .officeId(emp.getOfficeId())
                    .departmentId(emp.getDepartmentId())
                    .assessmentYear(year)
                    .target(t.target().trim())
                    .achievements(t.achievements().trim())
                    .sequenceOrder(seq++)
                    .build());
        }
        return mapper.toDetail(status);
    }

    // ── Authority reviews ACR — grade/remark/flag, and notify the employee ──
    @Transactional
    public AcrDetailView review(Long statusId, ReviewRequest req, UserAccount user) {
        if (!isAuthority(user))
            throw new ForbiddenException("Only the authority can review an ACR");

        EmployeeAcrStatus s = loadStatus(statusId);
        if (!Boolean.TRUE.equals(s.getIsAcrSubmitted()))
            throw new ForbiddenException("This ACR has not been submitted yet");

        if (req.flagged() && (req.flagReason() == null || req.flagReason().isBlank())) {
            throw new BadRequestException("A reason is required when an ACR is flagged");
        }

        s.setGrade(req.grade().trim());
        s.setAuthorityRemarks(req.remarks() != null ? req.remarks().trim() : null);
        s.setIsFlagged(req.flagged());
        s.setFlagReason(req.flagged() ? req.flagReason() : null);
        s.setReviewedBy(user.getEmployeeId());
        s.setReviewedAt(OffsetDateTime.now());
        statusRepository.save(s);

        // Notify the employee of the assessment outcome
        String title = req.flagged()
                ? "Your APAR has been flagged"
                : "Your APAR has been assessed";
        StringBuilder msg = new StringBuilder("Your APAR for ").append(s.getAssessmentYear())
                .append(" has been reviewed. Grade: ").append(s.getGrade()).append('.');
        if (req.flagged()) msg.append(" Flagged: ").append(s.getFlagReason());
        notificationService.notify(s.getEmployeeId(), user.getEmployeeId(),
                "ANNOUNCEMENT", title, msg.toString(), s.getAssessmentYear());

        return mapper.toDetail(s);
    }

    private EmployeeAcrStatus loadStatus(Long id) {
        return statusRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ACR not found: " + id));
    }

    private boolean isAuthority(UserAccount u) {
        return u.getRole() == Role.AUTHORITY;
    }
}
