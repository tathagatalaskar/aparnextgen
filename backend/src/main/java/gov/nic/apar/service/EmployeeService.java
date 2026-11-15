package gov.nic.apar.service;

import gov.nic.apar.dto.*;
import gov.nic.apar.entity.*;
import gov.nic.apar.exception.BadRequestException;
import gov.nic.apar.exception.NotFoundException;
import gov.nic.apar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Registration + authority-side employee management (add / edit / activate). */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeDetailsRepository employeeRepository;
    private final UserAccountRepository accountRepository;
    private final EmployeeAcrStatusRepository statusRepository;
    private final EmployeeAcrDetailRepository detailRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AcrMapper mapper;
    private final LookupService lookup;

    private static final Long DEFAULT_ORG = 1L;
    private static final Long DEFAULT_OFFICE = 101L;

    // ── Public self-registration ──
    @Transactional
    public MessageResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.findByEmail(email).isPresent())
            throw new BadRequestException("An account with this email already exists");

        EmployeeDetails emp = EmployeeDetails.builder()
                .organizationId(DEFAULT_ORG)
                .officeId(DEFAULT_OFFICE)
                .departmentId(req.departmentId())
                .empIniName(trimOrNull(req.empIniName()))
                .empFirstName(req.empFirstName().trim())
                .empMidName(trimOrNull(req.empMidName()))
                .empLastName(req.empLastName().trim())
                .gender(req.gender())
                .dateOfBirth(req.dateOfBirth())
                .designation(trimOrNull(req.designation()))
                .mobileNumber(trimOrNull(req.mobileNumber()))
                .email(email)
                // Public self-registration must be approved by an authority before sign-in.
                .isActive(false)
                .build();
        emp = employeeRepository.save(emp);   // employee_id generated from sequence

        UserAccount account = UserAccount.builder()
                .employeeId(emp.getEmployeeId())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.EMPLOYEE)
                .isActive(false)
                .build();
        accountRepository.save(account);

        return new MessageResponse("Registration submitted and is awaiting authority approval. You can sign in after your account is activated.");
    }

    // ── Authority: list all employees ──
    @Transactional(readOnly = true)
    public List<EmployeeView> listAll() {
        Map<Long, String> orgs = lookup.organizations();
        Map<Long, String> offices = lookup.offices();
        Map<Long, String> depts = lookup.departments();
        return employeeRepository.findAllByOrderByEmployeeIdAsc().stream()
                .map(e -> mapper.toEmployeeView(e, orgs, offices, depts))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView getOne(Long employeeId) {
        EmployeeDetails e = load(employeeId);
        return mapper.toEmployeeView(e, lookup.organizations(), lookup.offices(), lookup.departments());
    }

    // ── Authority: add an employee ──
    @Transactional
    public EmployeeView addEmployee(AddEmployeeRequest req) {
        EmployeeDetails emp = EmployeeDetails.builder()
                .organizationId(DEFAULT_ORG)
                .officeId(req.officeId() != null ? req.officeId() : DEFAULT_OFFICE)
                .departmentId(req.departmentId())
                .empIniName(trimOrNull(req.empIniName()))
                .empFirstName(req.empFirstName().trim())
                .empMidName(trimOrNull(req.empMidName()))
                .empLastName(req.empLastName().trim())
                .gender(req.gender())
                .dateOfBirth(req.dateOfBirth())
                .designation(trimOrNull(req.designation()))
                .joiningDate(req.joiningDate())
                .mobileNumber(trimOrNull(req.mobileNumber()))
                .email(trimOrNull(req.email()))
                .isActive(true)
                .build();
        emp = employeeRepository.save(emp);

        if (req.createLogin()) {
            if (req.email() == null || req.password() == null)
                throw new BadRequestException("Email and password are required to create a login");
            String email = req.email().trim().toLowerCase(Locale.ROOT);
            if (accountRepository.findByEmail(email).isPresent())
                throw new BadRequestException("An account with this email already exists");
            accountRepository.save(UserAccount.builder()
                    .employeeId(emp.getEmployeeId())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .role(Role.EMPLOYEE)
                    .isActive(true)
                    .build());
        }
        return mapper.toEmployeeView(emp, lookup.organizations(), lookup.offices(), lookup.departments());
    }

    // ── Authority: edit an employee ──
    @Transactional
    public EmployeeView updateEmployee(Long employeeId, UpdateEmployeeRequest req) {
        EmployeeDetails e = load(employeeId);
        e.setEmpIniName(trimOrNull(req.empIniName()));
        e.setEmpFirstName(req.empFirstName().trim());
        e.setEmpMidName(trimOrNull(req.empMidName()));
        e.setEmpLastName(req.empLastName().trim());
        e.setGender(req.gender());
        e.setDateOfBirth(req.dateOfBirth());
        e.setDepartmentId(req.departmentId());
        if (req.officeId() != null) e.setOfficeId(req.officeId());
        e.setDesignation(trimOrNull(req.designation()));
        e.setJoiningDate(req.joiningDate());
        e.setMobileNumber(trimOrNull(req.mobileNumber()));
        e.setEmail(trimOrNull(req.email()));
        employeeRepository.save(e);
        return mapper.toEmployeeView(e, lookup.organizations(), lookup.offices(), lookup.departments());
    }

    // ── Authority: activate / deactivate ──
    @Transactional
    public EmployeeView setActive(Long employeeId, boolean active, Long actorId) {
        EmployeeDetails e = load(employeeId);
        if (!active && hasAuthorityAccount(employeeId))
            throw new BadRequestException("A reporting authority account cannot be deactivated");
        e.setIsActive(active);
        employeeRepository.save(e);
        // also toggle the linked login account if any
        accountRepository.findAll().stream()
                .filter(a -> a.getEmployeeId().equals(employeeId))
                .forEach(a -> { a.setIsActive(active); accountRepository.save(a); });
        notificationService.notify(employeeId, actorId, "ANNOUNCEMENT",
                active ? "Your account has been activated" : "Your account has been deactivated",
                active ? "Your account is now active. You can log in and use the portal."
                       : "Your account has been deactivated by the administration.",
                null);
        return mapper.toEmployeeView(e, lookup.organizations(), lookup.offices(), lookup.departments());
    }

    // ── Authority: delete an employee (cascade clean-up) ──
    @Transactional
    public void deleteEmployee(Long employeeId) {
        EmployeeDetails e = load(employeeId);
        if (hasAuthorityAccount(employeeId))
            throw new BadRequestException("A reporting authority account cannot be deleted");
        // remove dependent rows first to satisfy FKs
        notificationRepository.deleteByRecipientEmployeeId(employeeId);
        statusRepository.findByEmployeeIdOrderByAssessmentYearDesc(employeeId)
                .forEach(st -> {
                    detailRepository.deleteByEmployeeIdAndAssessmentYear(employeeId, st.getAssessmentYear());
                    statusRepository.delete(st);
                });
        accountRepository.findAll().stream()
                .filter(a -> a.getEmployeeId().equals(employeeId))
                .forEach(accountRepository::delete);
        employeeRepository.delete(e);
    }

    // ── Employee: edit their own contact details ──
    @Transactional
    public EmployeeView updateProfile(Long employeeId, UpdateProfileRequest req) {
        EmployeeDetails e = load(employeeId);
        if (req.mobileNumber() != null) e.setMobileNumber(trimOrNull(req.mobileNumber()));
        if (req.email() != null) e.setEmail(trimOrNull(req.email()));
        if (req.designation() != null) e.setDesignation(trimOrNull(req.designation()));
        employeeRepository.save(e);
        return mapper.toEmployeeView(e, lookup.organizations(), lookup.offices(), lookup.departments());
    }

    private boolean hasAuthorityAccount(Long employeeId) {
        return accountRepository.findAll().stream()
                .anyMatch(a -> a.getEmployeeId().equals(employeeId) && a.getRole() == Role.AUTHORITY);
    }

    private EmployeeDetails load(Long id) {
        return employeeRepository.findByEmployeeId(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
