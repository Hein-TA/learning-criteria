package com.jdc.onestop.criteria;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.jdc.onestop.criteria.embeddable.AppointmentPk;
import com.jdc.onestop.criteria.embeddable.DoctorAppointmentPk;
import com.jdc.onestop.criteria.entity.Appointment;
import com.jdc.onestop.criteria.entity.AppointmentSeq;
import com.jdc.onestop.criteria.entity.Doctor;
import com.jdc.onestop.criteria.entity.DoctorAppointment;
import com.jdc.onestop.criteria.entity.Patient;
import com.jdc.onestop.criteria.input.AppointmentEditForm;
import com.jdc.onestop.criteria.input.AppointmentSearch;
import com.jdc.onestop.criteria.output.AppointmentDetailsInfo;
import com.jdc.onestop.criteria.output.AppointmentListItem;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;

public class AppointmentServiceImpl extends AbstractService implements AppointmentService {
	private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	public AppointmentServiceImpl(Supplier<EntityManager> emSupplier) {
		super(emSupplier);
	}

	@Override
	public AppointmentDetailsInfo create(AppointmentEditForm form) {
		
		validate(form);
		
		try(var em = emSupplier.get()) {
			
			em.getTransaction().begin();
			
			try {
				
				var appointmentId = new AppointmentPk(form.doctorId(), form.date(), form.startTime(), form.patientId());
				if(null != em.find(Appointment.class, appointmentId)) {
					// Throw Exception
					throw new HospitalBusinessException("You'd already taken appointment for %s %s.".formatted(form.date().format(DF), form.startTime()));
				}
				
				var doctor = em.find(Doctor.class, form.doctorId());
				
				if(null == doctor) {
					// Throw Exception
					throw new HospitalBusinessException("There is no doctor with id %d.".formatted(form.doctorId()));
				}
				
				var patient = em.find(Patient.class, form.patientId());
				
				if(null == patient) {
					// Throw Exception
					throw new HospitalBusinessException("There is no patient with id %d.".formatted(form.patientId()));
				}

				var schedule = doctor.getSchedule()
						.stream().filter(a -> a.getStartTime().equals(form.startTime()))
						.filter(a -> a.getDayOfWeek().equals(form.date().getDayOfWeek()))
						.findFirst().orElse(null);
				
				if(null == schedule) {
					// Throw Exception
					throw new HospitalBusinessException("There is no schedule for %s %s.".formatted(form.date().format(DF), form.startTime()));
				}

				var doctorAppointmentId = new DoctorAppointmentPk(form.date(), form.startTime(), form.doctorId());
				var doctorAppointment = getDoctorAppintment(em, doctor, doctorAppointmentId);
				var seq = getAppointmentSeq(em, doctorAppointmentId);
				
				var seqNumber = seq.getSeqNumber() + 1;
				
				if(seqNumber > schedule.getMaxPatient()) {
					// Throw Exception
					throw new HospitalBusinessException("Doctor can't accept appointment for %s %s.".formatted(form.date().format(DF), form.startTime()));
				}
				
				seq.setSeqNumber(seqNumber);

				var appointment = new Appointment();
				appointment.setDoctor(doctor);
				appointment.setPatient(patient);
				appointment.setReason(form.reason());
				appointment.setSeqNumber(seqNumber);
				appointment.setRegistAt(LocalDateTime.now());
				
				appointment.setId(new AppointmentPk(doctorAppointmentId, patient.getId()));
				
				if(seqNumber >= schedule.getMaxPatient()) {
					doctorAppointment.setAvailable(false);
				}
				
				em.persist(appointment);
				
				em.getTransaction().commit();
				
				return AppointmentDetailsInfo.from(appointment);
			} catch(HospitalBusinessException e) {
				em.getTransaction().rollback();
				throw e;
			} catch (Exception e) {
				em.getTransaction().rollback();
				throw new HospitalBusinessException("Platform Exception", e);
			}
		}
		
	}
	
	private void validate(AppointmentEditForm form) {
		
		if(null == form.date()) {
			throw new HospitalBusinessException("Please select appointment date.");
		}
		
		if(form.date().isBefore(LocalDate.now())) {
			throw new HospitalBusinessException("Please select today or future date for apointment date.");
		}
		
		if(null == form.startTime()) {
			throw new HospitalBusinessException("Please select start time.");
		}
		
	}

	private AppointmentSeq getAppointmentSeq(EntityManager em, DoctorAppointmentPk id) {
		var seq = em.find(AppointmentSeq.class, id);
		if(null == seq) {
			seq = new AppointmentSeq();
			seq.setId(id);
			em.persist(seq);
		}
		return seq;
	}
	
	private DoctorAppointment getDoctorAppintment(EntityManager em, Doctor doctor, DoctorAppointmentPk id) {
		var entity = em.find(DoctorAppointment.class, id);
		
		if(null == entity) {
			entity = new DoctorAppointment();
			entity.setId(id);
			entity.setAvailable(true);
			entity.setDoctor(doctor);
			
			em.persist(entity);
		}
		return entity;
	}
	
	@Override
	public List<AppointmentListItem> search(AppointmentSearch search) {
		try (var em = emSupplier.get()) {
			var cb = em.getCriteriaBuilder();
			var cq = cb.createQuery(AppointmentListItem.class);
			
			var root = cq.from(Appointment.class);
			
			cq.multiselect(
					root.get("doctor").get("id"),
					root.get("doctor").get("name"),
					root.get("patient").get("id"),
					root.get("patient").get("name"),
					root.get("patient").get("contact").get("phone"),
					root.get("id").get("appointmentDate"),
					root.get("id").get("startTime"),
					root.get("seqNumber"),
					root.get("registAt")
					);
			
			var params = new ArrayList<Predicate>();
			
			if (!isBlank(search.doctorName())) {
				params.add(cb.like(cb.lower(root.get("doctor").get("name")), search.doctorName().toLowerCase().concat("%")));
			}
			
			if (!isBlank(search.patientName())) {
				params.add(cb.like(cb.lower(root.get("patient").get("name")), search.patientName().toLowerCase().concat("%")));
			}
			
			if (!isBlank(search.patientPhone())) {
				params.add(cb.like(cb.lower(root.get("patient").get("contact").get("phone")), search.patientPhone().toLowerCase().concat("%")));
			}
			
			if (!isBlank(search.startTime())) {
				params.add(cb.equal(root.get("id").get("startTime"), search.startTime()));
			}
			
			if (null != search.from()) {
				params.add(cb.greaterThanOrEqualTo(root.get("id").get("appointmentDate"), search.from()));
			}
			
			if (null != search.to()) {
				params.add(cb.lessThanOrEqualTo(root.get("id").get("appointmentDate"), search.to()));
			}
			
			if (null != search.canceled()) {
				params.add(cb.equal(root.get("cancled"), search.canceled()));
			}
			
			cq.where(params.toArray(size -> new Predicate[size]));
			
			return em.createQuery(cq).getResultList();
		}
	}

}
