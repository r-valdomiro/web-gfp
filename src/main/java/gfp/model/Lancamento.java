package gfp.model;

import gfp.dto.SaldoCategoriaDto;
import gfp.type.CategoriaType;
import gfp.type.ContaType;
import gfp.type.FormaPagamentoType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import logus.commons.datetime.DateUtc;
import logus.commons.persistence.AbstractPersistentClass;
import logus.commons.persistence.hibernate.dao.HibernateDao;
import logus.commons.util.DateUtil;
import logus.commons.util.NumberUtil;

import org.apache.commons.beanutils.BeanUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

@Entity
public class Lancamento extends AbstractPersistentClass<Lancamento> {
	
	private static final long serialVersionUID = 1L;
	
	public static HibernateDao<Lancamento> dao;
	
	public static void excluir(final Categoria categoria) throws Exception {
		dao.deleteWhere("categoria = ?", categoria);
	}
	
	public static void excluir(final Conta conta) throws Exception {
		dao.deleteWhere("conta = ?", conta);
	}
	
	public static List<Lancamento> listarAVencer(final Long usuarioId,
			final Date previsaoPagamento, final CategoriaType categoria)
			throws Exception {
		return dao
				.where("usuario.id = ? and dataPrevisaoPagamento <= ? and categoria.tipo = ? and dataPagamento is null",
						usuarioId, previsaoPagamento, categoria.ordinal());
	}
	
	@SuppressWarnings("unchecked")
	public static List<Object[]> listarPrevisaoSaldoDiario(
			final Long usuarioId, final CategoriaType categoria,
			final Date dataInicio, final Date dataFinal) throws Exception {
		final Criteria c = dao.createCriteria();
		c.createAlias("categoria", "_categoria");
		
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.groupProperty("dataCompensacao"));
		p.add(Projections.groupProperty("categoria"));
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("usuario.id", usuarioId));
		c.add(Restrictions.eq("_categoria.tipo", categoria.ordinal()));
		c.add(Restrictions.eq("_categoria.estatistica", true));
		c.add(Restrictions.between("dataCompensacao", dataInicio, dataFinal));
		c.add(Restrictions.or(Restrictions.isNull("dataPagamento"),
				Restrictions.neProperty("dataPagamento", "dataCompensacao")));
		c.setProjection(p);
		return c.list();
	}
	
	public static List<Object[]> listarPrevisaoSaldoMensal(
			final Long usuarioId, final CategoriaType categoria,
			final Date dataInicio, final Date dataFinal) throws Exception {
		final List<Object[]> saldoDiario = listarPrevisaoSaldoDiario(usuarioId,
				categoria, dataInicio, dataFinal);
		final List<Object[]> result = new ArrayList<Object[]>();
		final Map<Date, Map<Categoria, Double>> dataCategoria = new HashMap<Date, Map<Categoria, Double>>();
		
		/* cria contas de or??amento inexistentes nos meses */
		for (final Categoria c : Categoria.dao
				.where("usuario.id = ? and tipo = ? and estatistica is true and valorOrcamento > 0",
						usuarioId, categoria.ordinal())) {
			mes: for (Date data = dataInicio; data.compareTo(dataFinal) <= 0; data = DateUtil
					.addMonth(data, 1)) {
				for (final Object[] o : saldoDiario) {
					if (c.equals(o[1]) &&
							DateUtil.month((Date) o[0]) == DateUtil.month(data) &&
							DateUtil.year((Date) o[0]) == DateUtil.year(data)) {
						continue mes;
					}
				}
				
				final Object o[] = { data, c, 0.0 };
				saldoDiario.add(o);
			}
		}
		
		/* desmembra categorias por data */
		for (final Object[] o : saldoDiario) {
			final Date data = DateUtil.time(
					DateUtil.firstDayOfMonth((Date) o[0]), 12, 0, 0);
			
			Map<Categoria, Double> categorias = dataCategoria.get(data);
			
			if (categorias == null) {
				categorias = new HashMap<Categoria, Double>();
				dataCategoria.put(data, categorias);
			}
			
			Double valor = categorias.get(o[1]);
			valor = valor == null ? (Double) o[2] : valor + (Double) o[2];
			categorias.put((Categoria) o[1], valor);
		}
		
		/* acumula saldos */
		for (final Date data : dataCategoria.keySet()) {
			final Map<Categoria, Double> categorias = dataCategoria.get(data);
			Double valor = 0.0;
			
			for (final Categoria c : categorias.keySet()) {
				if (c.getValorOrcamento() > 0 &&
						categorias.get(c) < c.getValorOrcamento()) {
					categorias.put(c, c.getValorOrcamento());
				}
				
				valor += categorias.get(c);
			}
			
			final Object o[] = { data, null, valor };
			result.add(o);
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static List<SaldoCategoriaDto> listarSaldoCategoriaMensal(
			final Long usuarioId, final Integer tipoCategoria,
			final Date dataInicio, final Date dataFinal) throws Exception {
		final List<SaldoCategoriaDto> result = new ArrayList<SaldoCategoriaDto>();
		
		final Criteria c = dao.createCriteria();
		c.createAlias("categoria", "_categoria");
		
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.groupProperty("categoria.id"));
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("usuario.id", usuarioId));
		c.add(Restrictions.eq("_categoria.tipo", tipoCategoria));
		c.add(Restrictions.eq("_categoria.estatistica", true));
		c.add(Restrictions.between("dataPrevisaoPagamento", dataInicio,
				dataFinal));
		c.setProjection(p);
		
		final List<Object[]> saldoCategoria = c.list();
		
		for (final Object[] o : saldoCategoria) {
			result.add(new SaldoCategoriaDto(dataInicio, Categoria.dao
					.find((Serializable) o[0]), (Double) o[1]));
		}
		
		return result;
	}
	
	public static double obterSaldoBloqueado(final Conta conta,
			final CategoriaType categoria, final Date dataSaldo)
			throws Exception {
		final Criteria c = dao.createCriteria();
		c.createAlias("categoria", "_categoria");
		
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("conta", conta));
		c.add(Restrictions.eq("_categoria.tipo", categoria.ordinal()));
		c.add(Restrictions.le("dataPagamento",
				DateUtil.time(dataSaldo, "23:59:59")));
		c.add(Restrictions.gt("dataCompensacao",
				DateUtil.time(dataSaldo, "00:00:00")));
		c.setProjection(p);
		
		final Double result = (Double) c.uniqueResult();
		return result != null ? NumberUtil.round(result, 2) : 0.0;
	}
	
	public static double obterSaldoDisponivel(final Conta conta,
			final CategoriaType categoria, final Date dataSaldo)
			throws Exception {
		final Criteria c = dao.createCriteria();
		c.createAlias("categoria", "_categoria");
		
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("conta", conta));
		c.add(Restrictions.eq("_categoria.tipo", categoria.ordinal()));
		c.add(Restrictions.le("dataPagamento",
				DateUtil.time(dataSaldo, "23:59:59")));
		c.add(Restrictions.le("dataCompensacao",
				DateUtil.time(dataSaldo, "23:59:59")));
		c.setProjection(p);
		
		final Double result = (Double) c.uniqueResult();
		return result != null ? NumberUtil.round(result, 2) : 0.0;
	}
	
	public static double obterSaldoDisponivel(final Conta conta,
			final FormaPagamentoType formaPagamento) throws Exception {
		final Criteria c = dao.createCriteria();
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("conta", conta));
		c.add(Restrictions.eq("formaPagamento", formaPagamento.ordinal()));
		c.add(Restrictions.isNull("dataPagamento"));
		c.setProjection(p);
		
		Double result = (Double) c.uniqueResult();
		result = result != null ? NumberUtil.round(result, 2) : 0.0;
		result = (formaPagamento == FormaPagamentoType.CREDITO_MASTERCARD ? conta
				.getLimiteMastercard() : conta.getLimiteVisa()) -
				result;
		return result;
	}
	
	public static double obterTotal(final Categoria categoria,
			final Date referencia) {
		final Criteria c = dao.createCriteria();
		
		final ProjectionList p = Projections.projectionList();
		p.add(Projections.sum("valorOriginal"));
		c.add(Restrictions.eq("categoria", categoria));
		c.add(Restrictions.between("dataPrevisaoPagamento",
				DateUtil.firstDayOfMonth(referencia),
				DateUtil.lastDayOfMonth(referencia)));
		c.setProjection(p);
		
		final Object result = c.uniqueResult();
		return result != null ? new Double(result.toString()).doubleValue()
				: 0.0;
	}
	
	@Id
	private Long id;
	
	@NotNull
	@Column(name = "valor_original")
	private double valorOriginal;
	
	@Size(max = 200)
	private String observacao;
	
	@NotNull
	@ManyToOne
	@JoinColumn(name = "usuario")
	private Usuario usuario;
	
	@NotNull
	@ManyToOne
	@JoinColumn(name = "categoria")
	private Categoria categoria;
	
	@ManyToOne
	@JoinColumn(name = "conta")
	private Conta conta;
	
	@NotNull
	@Column(name = "parcela_numero")
	private Integer parcelaNumero;
	
	@NotNull
	@Column(name = "parcela_quantidade")
	private Integer parcelaQuantidade;
	
	@NotNull
	@Column(name = "forma_pagamento")
	private Integer formaPagamento;
	
	@NotNull
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "data_compensacao")
	private Date dataCompensacao;
	
	@NotNull
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "data_previsao_pagamento")
	private Date dataPrevisaoPagamento;
	
	@NotNull
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "data_vencimento")
	private Date dataVencimento;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "data_pagamento")
	private Date dataPagamento;
	
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "original", fetch = FetchType.LAZY, orphanRemoval = true)
	private List<Lancamento> vinculados = new ArrayList<Lancamento>();
	
	@ManyToOne
	private Lancamento original;
	
	@ManyToOne
	@JoinColumn(name = "conta_transferencia")
	private Conta contaTransferencia;
	
	public Lancamento() {
		super();
	}
	
	public Lancamento(final Usuario usuario, final Categoria categoria,
			final double valorOriginal, final FormaPagamentoType formaPagamento) {
		super();
		this.usuario = usuario;
		this.categoria = categoria;
		this.valorOriginal = valorOriginal;
		this.dataVencimento = DateUtil.today();
		this.dataPrevisaoPagamento = DateUtil.today();
		this.formaPagamento = formaPagamento.ordinal();
		this.parcelaNumero = 1;
		this.parcelaQuantidade = 1;
	}
	
	private void calcularDataCompensacao() {
		Date previsaoPagamento = this.dataPagamento != null ? this.dataPagamento
				: this.dataPrevisaoPagamento;
		
		if (this.formaPagamento == FormaPagamentoType.CHEQUE.ordinal() &&
				this.categoria.getTipo() == CategoriaType.RECEITA.ordinal()) {
			final int prazoCompensacao = this.valorOriginal < 300 ? 2 : 1;
			int dias = 0;
			
			while (dias <= prazoCompensacao) {
				previsaoPagamento = DateUtil.add(previsaoPagamento, 1);
				
				if (DateUtil.dayOfWeek(previsaoPagamento) != Calendar.SATURDAY &&
						DateUtil.dayOfWeek(previsaoPagamento) != Calendar.SUNDAY) {
					dias++;
				}
				
				if (dias == prazoCompensacao &&
						DateUtil.dayOfWeek(previsaoPagamento) == Calendar.SATURDAY) {
					break;
				}
			}
		}
		
		this.dataCompensacao = previsaoPagamento;
	}
	
	@SuppressWarnings("unused")
	@Deprecated
	/**
	 * 04/11/12
	 */
	private void corrigirHorarioDeVerao() {
		this.dataVencimento = DateUtil.parseBRST(this.dataVencimento);
		this.dataPrevisaoPagamento = DateUtil
				.parseBRST(this.dataPrevisaoPagamento);
		this.dataPagamento = DateUtil.parseBRST(this.dataPagamento);
		this.dataCompensacao = DateUtil.parseBRST(this.dataCompensacao);
	}
	
	private void sincronizarVinculado() throws Exception {
		if (this.contaTransferencia != null) {
			if (this.vinculados == null || this.vinculados.size() == 0) {
				final Lancamento l = new Lancamento();
				BeanUtils.copyProperties(l, this);
				l.setId(this.id + 1);
				l.setConta(this.contaTransferencia);
				l.setOriginal(this);
				l.setContaTransferencia(null);
				l.setCategoria(Categoria.obterTransferencia(this.usuario));
				l.setFormaPagamento(this.contaTransferencia.getTipo().equals(
						ContaType.CARTEIRA.ordinal()) ? FormaPagamentoType.DINHEIRO
						.ordinal() : FormaPagamentoType.DEBITO.ordinal());
				
				if (this.vinculados == null) {
					this.vinculados = new ArrayList<Lancamento>();
				}
				
				this.vinculados.add(l);
			} else {
				final Lancamento vinculado = this.vinculados.get(0);
				
				vinculado
						.setFormaPagamento(this.contaTransferencia.getTipo()
								.equals(ContaType.CARTEIRA.ordinal()) ? FormaPagamentoType.DINHEIRO
								.ordinal() : FormaPagamentoType.DEBITO
								.ordinal());
				
				vinculado.setConta(this.contaTransferencia);
				vinculado.setDataCompensacao(this.dataCompensacao);
				vinculado.setDataPagamento(this.dataPagamento);
				vinculado.setDataPrevisaoPagamento(this.dataPrevisaoPagamento);
				vinculado.setDataVencimento(this.dataVencimento);
				vinculado.setValorOriginal(this.valorOriginal);
			}
		} else {
			if (this.vinculados != null && this.vinculados.size() > 0) {
				this.vinculados.remove(0).delete();
			}
		}
	}
	
	@Override
	protected HibernateDao<Lancamento> getDao() {
		return dao;
	}
	
	@Override
	protected void setDao(final HibernateDao<Lancamento> arg0) {
		dao = arg0;
	}
	
	public Categoria getCategoria() {
		return this.categoria;
	}
	
	public Conta getConta() {
		return this.conta;
	}
	
	public Conta getContaTransferencia() {
		return this.contaTransferencia;
	}
	
	public Date getDataCompensacao() {
		return this.dataCompensacao;
	}
	
	public String getDataCompensacaoUtc() {
		return DateUtc.get(this.dataCompensacao);
	}
	
	public Date getDataPagamento() {
		return this.dataPagamento;
	}
	
	public String getDataPagamentoUtc() {
		return DateUtc.get(this.dataPagamento);
	}
	
	public Date getDataPrevisaoPagamento() {
		return this.dataPrevisaoPagamento;
	}
	
	public String getDataPrevisaoPagamentoUtc() {
		return DateUtc.get(this.dataPrevisaoPagamento);
	}
	
	public Date getDataVencimento() {
		return this.dataVencimento;
	}
	
	public String getDataVencimentoUtc() {
		return DateUtc.get(this.dataVencimento);
	}
	
	public Integer getFormaPagamento() {
		return this.formaPagamento;
	}
	
	public Long getId() {
		return this.id;
	}
	
	public String getObservacao() {
		return this.observacao;
	}
	
	public Lancamento getOriginal() {
		return this.original;
	}
	
	public Integer getParcelaNumero() {
		return this.parcelaNumero;
	}
	
	public Integer getParcelaQuantidade() {
		return this.parcelaQuantidade;
	}
	
	public Usuario getUsuario() {
		return this.usuario;
	}
	
	public double getValorOriginal() {
		return this.valorOriginal;
	}
	
	public List<Lancamento> getVinculados() {
		return this.vinculados;
	}
	
	public void setCategoria(final Categoria categoria) {
		this.categoria = categoria;
	}
	
	public void setConta(final Conta conta) {
		this.conta = conta;
	}
	
	public void setContaTransferencia(final Conta contaTransferencia) {
		this.contaTransferencia = contaTransferencia;
	}
	
	public void setDataCompensacao(final Date dataCompensacao) {
		this.dataCompensacao = dataCompensacao;
	}
	
	public void setDataCompensacaoUtc(final String dataCompensacao) {
		this.dataCompensacao = DateUtc.set(dataCompensacao);
	}
	
	public void setDataPagamento(final Date dataPagamento) {
		this.dataPagamento = dataPagamento;
	}
	
	public void setDataPagamentoUtc(final String dataPagamento) {
		this.dataPagamento = DateUtc.set(dataPagamento);
	}
	
	public void setDataPrevisaoPagamento(final Date dataPrevisaoPagamento) {
		this.dataPrevisaoPagamento = dataPrevisaoPagamento;
	}
	
	public void setDataPrevisaoPagamentoUtc(final String dataPrevisaoPagamento) {
		this.dataPrevisaoPagamento = DateUtc.set(dataPrevisaoPagamento);
	}
	
	public void setDataVencimento(final Date dataVencimento) {
		this.dataVencimento = dataVencimento;
	}
	
	public void setDataVencimentoUtc(final String dataVencimento) {
		this.dataVencimento = DateUtc.set(dataVencimento);
	}
	
	public void setFormaPagamento(final Integer formaPagamento) {
		this.formaPagamento = formaPagamento;
	}
	
	public void setId(final Long id) {
		this.id = id;
	}
	
	public void setObservacao(final String observacao) {
		this.observacao = observacao;
	}
	
	public void setOriginal(final Lancamento original) {
		this.original = original;
	}
	
	public void setParcelaNumero(final Integer parcelaNumero) {
		this.parcelaNumero = parcelaNumero;
	}
	
	public void setParcelaQuantidade(final Integer parcelaQuantidade) {
		this.parcelaQuantidade = parcelaQuantidade;
	}
	
	public void setUsuario(final Usuario usuario) {
		this.usuario = usuario;
	}
	
	public void setValorOriginal(final double valorOriginal) {
		this.valorOriginal = valorOriginal;
	}
	
	public void setVinculados(final List<Lancamento> vinculados) {
		this.vinculados = vinculados;
	}
	
	@Override
	public void validate() throws Exception {
		super.validate();
		
		if (this.id == null) {
			this.id = dao.getNextSequence(this, "id").longValue();
		}
		
		calcularDataCompensacao();
		sincronizarVinculado();
	}
	
}
