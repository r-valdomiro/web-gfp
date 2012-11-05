package gfp.dto;

import gfp.model.Conta;
import gfp.model.Usuario;
import gfp.type.LancamentoPeriodoType;
import gfp.type.LancamentoSituacaoType;

import java.util.Date;

import logus.commons.datetime.DateUtil;

public class LancamentoDto {
	
	private Long idUsuario;
	
	private int tipoPeriodo;
	
	private int situacao;
	
	private Date dataInicio;
	
	private Date dataFinal;
	
	private Long categoria;
	
	private Conta conta;
	
	private String observacao;
	
	public LancamentoDto() {
		super();
	}
	
	public LancamentoDto(final Usuario usuario) throws Exception {
		super();
		this.idUsuario = usuario.getId();
		this.dataInicio = DateUtil.today();
		this.dataFinal = DateUtil.today();
		this.tipoPeriodo = LancamentoPeriodoType.VENCIMENTO.ordinal();
		this.situacao = LancamentoSituacaoType.INDEFINIDA.ordinal();
	}
	
	public Long getCategoria() {
		return this.categoria;
	}
	
	public Conta getConta() {
		return this.conta;
	}
	
	public Date getDataFinal() {
//		return DateUtil.time(this.dataFinal, "23:59:59");
		return this.dataFinal;
	}
	
	public Date getDataInicio() {
//		return DateUtil.time(this.dataInicio, "00:00:00");
		return this.dataInicio;
	}
	
	public String getDataInicioString() {
		return DateUtil.toDateTimeString(this.dataInicio);
	}
	
	public void setDataInicioString(final String arg0) {
		this.dataInicio = DateUtil.toDateTime(arg0);
	}
	
	public Long getIdUsuario() {
		return this.idUsuario;
	}
	
	public String getObservacao() {
		return this.observacao;
	}
	
	public int getSituacao() {
		return this.situacao;
	}
	
	public int getTipoPeriodo() {
		return this.tipoPeriodo;
	}
	
	public void setCategoria(final Long categoria) {
		this.categoria = categoria;
	}
	
	public void setConta(final Conta conta) {
		this.conta = conta;
	}
	
	public void setDataFinal(final Date dataFinal) {
// this.dataFinal = DateUtil.parseBRST(dataFinal);
		this.dataFinal = dataFinal;
	}
	
	public void setDataInicio(final Date dataInicio) {
// this.dataInicio = DateUtil.parseBRST(dataInicio);
		this.dataInicio = dataInicio;
	}
	
	public void setIdUsuario(final Long idUsuario) {
		this.idUsuario = idUsuario;
	}
	
	public void setObservacao(final String observacao) {
		this.observacao = observacao;
	}
	
	public void setSituacao(final int situacao) {
		this.situacao = situacao;
	}
	
	public void setTipoPeriodo(final int tipoPeriodo) {
		this.tipoPeriodo = tipoPeriodo;
	}
	
}
