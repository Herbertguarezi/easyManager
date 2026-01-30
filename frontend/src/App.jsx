import Footer from "./Components/Footer"
import Header from "./Components/Header"
import Produto from "./Components/Produto"
import Produtos from "./Components/Produtos";

function App() {

  function criarProduto(){
    const formData = new FormData();
    const file = document.querySelector("#file")
    formData.append("name", "Mouse Gamer");
    formData.append("amount", 10);
    formData.append("file", file.files[0])
    formData.append("barcode", "1111")

    for (let [key, value] of formData.entries()) {
      console.log(key, value);
    }
    fetch("http://localhost:8080/products", {
    method: "POST",
    body: formData,
  })
  .then(res => res.json())
  .then(data => {
    console.log("Produto criado:", data);
  })
  .catch(err => {
    console.error("Erro ao criar produto:", err);
  });



  }

  return (
    <>
      <Header/>
      <h1>Herbert</h1>
      <input type="file" name="file" id="file"/>
      <button onClick={() => criarProduto()}>Clique</button>
      <Produtos/>
      <Footer/>
    </>
  )
}

export default App
